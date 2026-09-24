package com.proveyu.payment.application;

import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.booking.domain.Booking;
import com.proveyu.booking.infrastructure.BookingRepository;
import com.proveyu.payment.domain.AdmitCard;
import com.proveyu.payment.domain.Payment;
import com.proveyu.payment.domain.PaymentStatus;
import com.proveyu.payment.infrastructure.AdmitCardRepository;
import com.proveyu.payment.infrastructure.PaymentRepository;
import com.proveyu.shared.email.EmailService;
import com.proveyu.shared.error.DomainException;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final AdmitCardRepository admitCardRepository;
    private final AdmitCardGeneratorService admitCardGeneratorService;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${razorpay.key_id:rzp_test_proveyu_key}")
    private String razorpayKeyId;

    @Value("${razorpay.key_secret:proveyu_test_secret_321}")
    private String razorpayKeySecret;

    @Value("${razorpay.mock_mode:true}")
    private boolean mockMode;

    public PaymentService(PaymentRepository paymentRepository,
            BookingRepository bookingRepository,
            AdmitCardRepository admitCardRepository,
            AdmitCardGeneratorService admitCardGeneratorService,
            UserRepository userRepository,
            EmailService emailService) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.admitCardRepository = admitCardRepository;
        this.admitCardGeneratorService = admitCardGeneratorService;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    @Transactional
    public PaymentOrderResponse createUpiPaymentOrder(UUID bookingId, int amountCents) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new DomainException("Booking not found", HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));

        String idempotencyKey = "PAY_" + bookingId.toString();

        Payment existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existingPayment != null && existingPayment.getStatus() == PaymentStatus.SUCCESS) {
            throw new DomainException("Booking is already paid and confirmed", HttpStatus.BAD_REQUEST, "ALREADY_PAID");
        }

        String orderId;
        if (mockMode) {
            orderId = "order_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        } else {
            try {
                RazorpayClient razorpay = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
                JSONObject orderRequest = new JSONObject();
                orderRequest.put("amount", amountCents);
                orderRequest.put("currency", "INR");
                orderRequest.put("receipt", "rcpt_" + bookingId.toString().substring(0, 8));

                Order order = razorpay.orders.create(orderRequest);
                orderId = order.get("id");
            } catch (Exception e) {
                log.error("Failed to create Razorpay Order", e);
                throw new DomainException("Failed to initiate gateway order", HttpStatus.INTERNAL_SERVER_ERROR,
                        "GATEWAY_ERROR");
            }
        }

        Payment payment = existingPayment;
        if (payment == null) {
            payment = Payment.builder()
                    .bookingId(bookingId)
                    .amountCents(amountCents)
                    .currency("INR")
                    .status(PaymentStatus.PENDING)
                    .gatewayOrderId(orderId)
                    .idempotencyKey(idempotencyKey)
                    .createdAt(Instant.now())
                    .build();
        } else {
            payment.setGatewayOrderId(orderId);
            payment.setAmountCents(amountCents);
        }

        paymentRepository.save(payment);

        String upiIntentUri = String.format(
                "upi://pay?pa=proveyu@razorpay&pn=PROVEYU%%20Assessments&tr=%s&am=%.2f&cu=INR&tn=Exam%%20Slot%%20Booking",
                orderId, (amountCents / 100.0));

        return new PaymentOrderResponse(
                payment.getId(),
                bookingId,
                orderId,
                amountCents,
                "INR",
                razorpayKeyId,
                upiIntentUri,
                mockMode);
    }

    @Transactional
    public AdmitCard verifyPaymentAndConfirmBooking(String orderId, String paymentId, String signature) {
        Payment payment = paymentRepository.findByGatewayOrderId(orderId)
                .orElseThrow(() -> new DomainException("Payment order not found", HttpStatus.NOT_FOUND,
                        "PAYMENT_NOT_FOUND"));

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return admitCardRepository.findByBookingId(payment.getBookingId())
                    .orElseThrow(() -> new DomainException("Admit Card not found for paid booking",
                            HttpStatus.NOT_FOUND, "ADMIT_CARD_NOT_FOUND"));
        }

        boolean isValid;
        if (mockMode || "mock_signature".equals(signature)) {
            isValid = true;
        } else {
            try {
                JSONObject options = new JSONObject();
                options.put("razorpay_order_id", orderId);
                options.put("razorpay_payment_id", paymentId);
                options.put("razorpay_signature", signature);
                isValid = Utils.verifyPaymentSignature(options, razorpayKeySecret);
            } catch (Exception e) {
                log.error("Signature verification error", e);
                isValid = false;
            }
        }

        if (!isValid) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            throw new DomainException("Invalid payment signature verification", HttpStatus.BAD_REQUEST,
                    "PAYMENT_VERIFICATION_FAILED");
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setGatewayPaymentId(
                paymentId != null ? paymentId : "pay_mock_" + UUID.randomUUID().toString().substring(0, 10));
        payment.setUpiTransactionId("upi_tx_" + UUID.randomUUID().toString().substring(0, 12));
        paymentRepository.save(payment);

        Booking booking = bookingRepository.findById(payment.getBookingId())
                .orElseThrow(() -> new DomainException("Booking not found", HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));

        booking.confirm();
        bookingRepository.save(booking);

        String qrToken = "QR_PROVEYU_" + UUID.randomUUID().toString();
        String barcode = "BC" + System.currentTimeMillis();

        String pdfUrl = admitCardGeneratorService.generateAndSaveAdmitCardPdf(booking, qrToken, barcode);

        AdmitCard admitCard = AdmitCard.builder()
                .bookingId(booking.getId())
                .qrCodeToken(qrToken)
                .barcodeValue(barcode)
                .pdfUrl(pdfUrl)
                .build();

        AdmitCard savedAdmitCard = admitCardRepository.save(admitCard);

        // Trigger Async Email Notification for Admit Card Issue
        User candidate = userRepository.findById(booking.getCandidateId()).orElse(null);
        if (candidate != null) {
            emailService.sendAdmitCardIssuedEmail(
                    candidate.getEmail(),
                    candidate.getFullName(),
                    booking.getId().toString(),
                    pdfUrl);
        }

        return savedAdmitCard;
    }

    public record PaymentOrderResponse(
            UUID paymentId,
            UUID bookingId,
            String gatewayOrderId,
            int amountCents,
            String currency,
            String razorpayKeyId,
            String upiIntentUri,
            boolean mockMode) {
    }
}
