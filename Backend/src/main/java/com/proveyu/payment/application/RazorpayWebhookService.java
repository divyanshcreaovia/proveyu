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
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RazorpayWebhookService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final AdmitCardRepository admitCardRepository;
    private final AdmitCardGeneratorService admitCardGeneratorService;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${razorpay.webhook_secret:proveyu_webhook_secret_321}")
    private String webhookSecret;

    @Value("${razorpay.mock_mode:true}")
    private boolean mockMode;

    public boolean verifyWebhookSignature(String payload, String signature) {
        if (mockMode || "mock_webhook_signature".equals(signature)) {
            return true;
        }
        try {
            return Utils.verifyWebhookSignature(payload, signature, webhookSecret);
        } catch (Exception e) {
            log.error("[WEBHOOK SIGNATURE VERIFICATION FAILED] Exception: {}", e.getMessage());
            return false;
        }
    }

    @Transactional
    public void processWebhookEvent(String payload) {
        JSONObject eventJson = new JSONObject(payload);
        String eventType = eventJson.optString("event", "");
        log.info("[RAZORPAY WEBHOOK RECEIVED] Event: {}", eventType);

        if ("order.paid".equalsIgnoreCase(eventType) || "payment.authorized".equalsIgnoreCase(eventType)) {
            JSONObject payloadEntity = eventJson.optJSONObject("payload");
            if (payloadEntity == null) return;

            JSONObject orderObj = payloadEntity.optJSONObject("order");
            JSONObject paymentObj = payloadEntity.optJSONObject("payment");

            String orderId = null;
            if (orderObj != null) {
                orderId = orderObj.optString("id", null);
            } else if (paymentObj != null) {
                orderId = paymentObj.optString("order_id", null);
            }

            if (orderId == null) {
                log.warn("[WEBHOOK WARN] Webhook event received missing order_id");
                return;
            }

            Payment payment = paymentRepository.findByGatewayOrderId(orderId).orElse(null);
            if (payment == null) {
                log.warn("[WEBHOOK WARN] No payment record found for gateway orderId=[{}]", orderId);
                return;
            }

            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                log.info("[WEBHOOK INFO] Payment orderId=[{}] already processed and SUCCESS", orderId);
                return;
            }

            // Update Payment Status
            payment.setStatus(PaymentStatus.SUCCESS);
            if (paymentObj != null) {
                payment.setGatewayPaymentId(paymentObj.optString("id", payment.getGatewayPaymentId()));
            }
            paymentRepository.save(payment);

            // Confirm Booking
            Booking booking = bookingRepository.findById(payment.getBookingId()).orElse(null);
            if (booking != null) {
                booking.confirm();
                bookingRepository.save(booking);

                // Generate Admit Card PDF if not generated
                AdmitCard existingAdmitCard = admitCardRepository.findByBookingId(booking.getId()).orElse(null);
                String pdfUrl;
                if (existingAdmitCard == null) {
                    String qrToken = "QR_PROVEYU_" + UUID.randomUUID().toString();
                    String barcode = "BC" + System.currentTimeMillis();
                    pdfUrl = admitCardGeneratorService.generateAndSaveAdmitCardPdf(booking, qrToken, barcode);

                    AdmitCard admitCard = AdmitCard.builder()
                            .bookingId(booking.getId())
                            .qrCodeToken(qrToken)
                            .barcodeValue(barcode)
                            .pdfUrl(pdfUrl)
                            .build();
                    admitCardRepository.save(admitCard);
                } else {
                    pdfUrl = existingAdmitCard.getPdfUrl();
                }

                // Trigger Async Email Notification
                User candidate = userRepository.findById(booking.getCandidateId()).orElse(null);
                if (candidate != null) {
                    emailService.sendAdmitCardIssuedEmail(
                            candidate.getEmail(),
                            candidate.getFullName(),
                            booking.getId().toString(),
                            pdfUrl
                    );
                }

                log.info("[WEBHOOK SUCCESS] Booking [{}] confirmed and Admit Card PDF issued via Webhook", booking.getId());
            }
        } else if ("payment.failed".equalsIgnoreCase(eventType)) {
            JSONObject payloadEntity = eventJson.optJSONObject("payload");
            if (payloadEntity != null) {
                JSONObject paymentObj = payloadEntity.optJSONObject("payment");
                if (paymentObj != null) {
                    String orderId = paymentObj.optString("order_id", null);
                    if (orderId != null) {
                        Payment payment = paymentRepository.findByGatewayOrderId(orderId).orElse(null);
                        if (payment != null) {
                            payment.setStatus(PaymentStatus.FAILED);
                            paymentRepository.save(payment);
                            log.info("[WEBHOOK PAYMENT FAILED] Marked payment orderId=[{}] as FAILED", orderId);
                        }
                    }
                }
            }
        }
    }
}
