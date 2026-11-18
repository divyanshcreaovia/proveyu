package com.proveyu.student.interview.application;

import com.proveyu.college.application.CollegeDirectory;
import com.proveyu.student.application.StudentPortalService;
import com.proveyu.college.domain.CollegeProfile;
import com.proveyu.college.domain.CollegeStudent;
import com.proveyu.college.infrastructure.CollegeProfileRepository;
import com.proveyu.college.infrastructure.CollegeStudentRepository;
import com.proveyu.student.interview.domain.InterviewCredit;
import com.proveyu.student.interview.domain.InterviewModel.AdminPurchase;
import com.proveyu.student.interview.domain.InterviewModel.PurchaseOrder;
import com.proveyu.student.interview.domain.InterviewModel.PurchaseStatus;
import com.proveyu.student.interview.domain.InterviewModel.StudentPurchase;
import com.proveyu.student.interview.domain.InterviewPurchase;
import com.proveyu.student.interview.infrastructure.InterviewCreditRepository;
import com.proveyu.student.interview.infrastructure.InterviewPurchaseRepository;
import com.proveyu.shared.error.DomainException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Students buy AI interviews by UPI QR, paid to the platform's own UPI ID. The server fixes the price and puts a short reference in
 * the payment note. The student then types the 12-digit UPI reference (UTR); a platform admin checks the bank statement and
 * approves, which adds the interviews. A UTR can be claimed only once (unique in the database), and every change to a purchase
 * locks its row first, so a purchase is credited exactly once.
 */
@Slf4j
@Service
public class InterviewPurchaseService {

    private static final Pattern UTR = Pattern.compile("\\d{12}");
    private static final int MAX_WAITING = 3;
    private static final Duration OPEN_FOR = Duration.ofHours(24);
    private static final char[] REF_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray(); // no 0/O or 1/I to misread
    private final SecureRandom random = new SecureRandom();

    private final InterviewPurchaseRepository purchases;
    private final InterviewCreditRepository credits;
    private final StudentPortalService portal;
    private final CollegeStudentRepository students;
    private final CollegeProfileRepository colleges;
    private final TransactionTemplate tx;
    private final int priceRupees;
    private final int maxPerPurchase;
    private final String upiId;
    private final String upiName;

    public InterviewPurchaseService(InterviewPurchaseRepository purchases, InterviewCreditRepository credits, StudentPortalService portal,
                                    CollegeStudentRepository students, CollegeProfileRepository colleges, PlatformTransactionManager txm,
                                    @Value("${interview.price-rupees:20}") int priceRupees,
                                    @Value("${interview.max-per-purchase:10}") int maxPerPurchase,
                                    @Value("${interview.upi.id:}") String upiId,
                                    @Value("${interview.upi.name:ProveYu}") String upiName) {
        this.purchases = purchases;
        this.credits = credits;
        this.portal = portal;
        this.students = students;
        this.colleges = colleges;
        this.tx = new TransactionTemplate(txm);
        this.priceRupees = priceRupees;
        this.maxPerPurchase = maxPerPurchase;
        this.upiId = upiId == null ? "" : upiId.trim();
        this.upiName = upiName == null || upiName.isBlank() ? "ProveYu" : upiName.trim();
        if (!ready()) log.warn("AI interview payments: UPI_ID is not set, buying is off");
    }

    public int priceRupees() {
        return priceRupees;
    }

    public int maxPerPurchase() {
        return maxPerPurchase;
    }

    /** False until a UPI ID is configured; the page then says buying is not available yet. */
    public boolean ready() {
        return upiId.contains("@");
    }

    // ================================================================== student

    public PurchaseOrder createOrder(UUID userId, int quantity) {
        if (quantity < 1 || quantity > maxPerPurchase) {
            throw new DomainException("You can buy 1 to " + maxPerPurchase + " interviews at a time", HttpStatus.BAD_REQUEST, "INVALID_QUANTITY");
        }
        if (!ready()) throw new DomainException("Payments are not available yet. Please try again later.", HttpStatus.SERVICE_UNAVAILABLE, "PAYMENTS_UNAVAILABLE");
        CollegeStudent s = portal.me(userId);
        int amountPaise = quantity * priceRupees * 100;
        long waiting = purchases.countByUserIdAndStatus(userId, PurchaseStatus.SUBMITTED);
        if (waiting >= MAX_WAITING) {
            throw new DomainException("You have " + waiting + " payments waiting for verification. Please wait until they are checked.",
                    HttpStatus.CONFLICT, "TOO_MANY_PENDING");
        }
        // Pressing Pay again (e.g. after closing the QR) reuses the open request, so the student keeps one reference to pay against.
        InterviewPurchase p = openRequest(userId).filter(x -> x.getQuantity() == quantity && x.getAmountPaise() == amountPaise)
                .orElseGet(() -> purchases.save(InterviewPurchase.builder().collegeId(s.getCollegeId()).studentId(s.getId()).userId(userId)
                        .quantity(quantity).amountPaise(amountPaise).status(PurchaseStatus.PENDING).reference(reference()).build()));
        return new PurchaseOrder(p.getId(), p.getReference(), quantity, amountPaise / 100, upiUri(amountPaise, p.getReference()), upiName, upiId);
    }

    /** The student has paid and types the 12-digit UPI reference. The purchase then waits for an admin. */
    public StudentPurchase submitUtr(UUID userId, UUID purchaseId, String rawUtr) {
        String utr = rawUtr == null ? "" : rawUtr.replaceAll("[\\s-]", "");
        if (!UTR.matcher(utr).matches()) {
            throw new DomainException("Enter the 12-digit UPI reference number (UTR) from your payment app", HttpStatus.BAD_REQUEST, "INVALID_UTR");
        }
        try {
            return tx.execute(st -> {
                InterviewPurchase p = purchases.lockById(purchaseId).filter(x -> x.getUserId().equals(userId))
                        .orElseThrow(() -> new DomainException("Payment not found", HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND"));
                if (p.getStatus() != PurchaseStatus.PENDING) {
                    throw new DomainException("This payment was already submitted", HttpStatus.CONFLICT, "ALREADY_SUBMITTED");
                }
                p.setUtr(utr);
                p.setStatus(PurchaseStatus.SUBMITTED);
                p.setSubmittedAt(Instant.now());
                purchases.saveAndFlush(p);
                return studentView(p);
            });
        } catch (DataIntegrityViolationException e) {
            throw new DomainException("This UPI reference number was already used for another payment", HttpStatus.CONFLICT, "UTR_ALREADY_USED");
        }
    }

    /**
     * Purchases the student should see: the open one not yet submitted (last 24 hours, so a student who paid and closed the window
     * can still enter the reference), and the recent waiting, approved or rejected ones.
     */
    public List<StudentPurchase> recent(UUID userId) {
        List<StudentPurchase> out = new ArrayList<>();
        openRequest(userId).ifPresent(p -> out.add(studentView(p)));
        purchases.findTop10ByUserIdAndStatusInOrderByCreatedAtDesc(userId, List.of(PurchaseStatus.SUBMITTED, PurchaseStatus.PAID, PurchaseStatus.REJECTED))
                .stream().map(this::studentView).forEach(out::add);
        return out;
    }

    private Optional<InterviewPurchase> openRequest(UUID userId) {
        Instant since = Instant.now().minus(OPEN_FOR);
        return purchases.findTop10ByUserIdAndStatusInOrderByCreatedAtDesc(userId, List.of(PurchaseStatus.PENDING)).stream()
                .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().isAfter(since)).findFirst();
    }

    // ================================================================== platform admin

    /** Payments to check against the bank statement. status null = the ones waiting (SUBMITTED). */
    public List<AdminPurchase> forReview(PurchaseStatus status) {
        List<InterviewPurchase> list = status == null || status == PurchaseStatus.SUBMITTED
                ? purchases.findAllByStatusOrderBySubmittedAtAsc(PurchaseStatus.SUBMITTED)
                : purchases.findTop100ByStatusOrderByReviewedAtDesc(status);
        return adminViews(list);
    }

    /** The admin found the money in the bank: add the interviews. A wrongly rejected payment can be approved later too. */
    public AdminPurchase approve(UUID adminUserId, UUID purchaseId) {
        tx.executeWithoutResult(st -> {
            InterviewPurchase p = adminLock(purchaseId);
            if (p.getStatus() == PurchaseStatus.PAID) return;
            if (p.getStatus() != PurchaseStatus.SUBMITTED && p.getStatus() != PurchaseStatus.REJECTED) {
                throw new DomainException("The student has not entered a payment reference yet", HttpStatus.CONFLICT, "NOT_SUBMITTED");
            }
            p.setStatus(PurchaseStatus.PAID);
            p.setPaidAt(Instant.now());
            p.setReviewedAt(Instant.now());
            p.setReviewedBy(adminUserId);
            p.setReviewNote(null);
            purchases.save(p);
            InterviewCredit c = credits.findById(p.getStudentId())
                    .orElseGet(() -> InterviewCredit.builder().studentId(p.getStudentId()).collegeId(p.getCollegeId()).build());
            c.setPaidBalance(c.getPaidBalance() + p.getQuantity());
            credits.saveAndFlush(c);
        });
        return adminViews(List.of(purchases.findById(purchaseId).orElseThrow())).get(0);
    }

    public AdminPurchase reject(UUID adminUserId, UUID purchaseId, String note) {
        String reason = note == null ? "" : note.trim();
        if (reason.length() < 3) throw new DomainException("Give a short reason for the student", HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        tx.executeWithoutResult(st -> {
            InterviewPurchase p = adminLock(purchaseId);
            if (p.getStatus() != PurchaseStatus.SUBMITTED) {
                throw new DomainException("Only payments waiting for verification can be rejected", HttpStatus.CONFLICT, "NOT_SUBMITTED");
            }
            p.setStatus(PurchaseStatus.REJECTED);
            p.setReviewedAt(Instant.now());
            p.setReviewedBy(adminUserId);
            p.setReviewNote(reason.length() > 300 ? reason.substring(0, 300) : reason);
            purchases.save(p);
        });
        return adminViews(List.of(purchases.findById(purchaseId).orElseThrow())).get(0);
    }

    // ================================================================== helpers

    private List<AdminPurchase> adminViews(List<InterviewPurchase> list) {
        Map<UUID, CollegeStudent> byId = students.findAllById(list.stream().map(InterviewPurchase::getStudentId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(CollegeStudent::getId, Function.identity()));
        Map<UUID, String> collegeNames = colleges.findAllById(list.stream().map(InterviewPurchase::getCollegeId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(CollegeProfile::getCollegeId, c -> Objects.toString(c.getCollegeName(), ""), (a, b) -> a));
        return list.stream().map(p -> {
            CollegeStudent s = byId.get(p.getStudentId());
            return new AdminPurchase(p.getId(), p.getReference(), s == null ? "(deleted student)" : CollegeDirectory.fullName(s), s == null ? null : s.getEmail(),
                    s == null ? null : s.getRollNumber(), collegeNames.get(p.getCollegeId()), p.getQuantity(), p.getAmountPaise() / 100,
                    p.getStatus(), p.getUtr(), p.getCreatedAt(), p.getSubmittedAt(), p.getReviewedAt(), p.getReviewNote());
        }).toList();
    }

    private InterviewPurchase adminLock(UUID id) {
        return purchases.lockById(id).orElseThrow(() -> new DomainException("Payment not found", HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND"));
    }

    /** Short code shown to the student and put in the UPI payment note, so the admin can match a bank entry to the request. */
    private String reference() {
        StringBuilder b = new StringBuilder("PY");
        for (int i = 0; i < 8; i++) b.append(REF_CHARS[random.nextInt(REF_CHARS.length)]);
        return b.toString();
    }

    /** UPI deep link (NPCI format). The QR on the page encodes exactly this, so any UPI app opens it with the amount filled in. */
    String upiUri(int amountPaise, String reference) {
        return "upi://pay?pa=" + enc(upiId) + "&pn=" + enc(upiName) + "&am=" + String.format(Locale.ROOT, "%d.%02d", amountPaise / 100, amountPaise % 100)
                + "&cu=INR&tn=" + enc(upiName + " " + reference);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private StudentPurchase studentView(InterviewPurchase p) {
        boolean open = p.getStatus() == PurchaseStatus.PENDING;
        return new StudentPurchase(p.getId(), p.getReference(), p.getQuantity(), p.getAmountPaise() / 100, p.getStatus(), p.getUtr(),
                p.getCreatedAt(), p.getSubmittedAt(), p.getReviewedAt(), p.getReviewNote(),
                open ? upiUri(p.getAmountPaise(), p.getReference()) : null, open ? upiName : null, open ? upiId : null);
    }
}
