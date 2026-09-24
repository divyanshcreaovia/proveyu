package com.proveyu.admin.application;

import com.proveyu.assessment.domain.Exam;
import com.proveyu.assessment.domain.Skill;
import com.proveyu.assessment.infrastructure.ExamRepository;
import com.proveyu.assessment.infrastructure.SkillRepository;
import com.proveyu.auth.domain.Role;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.booking.infrastructure.BookingRepository;
import com.proveyu.payment.infrastructure.PaymentRepository;
import com.proveyu.recruiter.domain.PlacementStatus;
import com.proveyu.recruiter.infrastructure.InterviewInviteRepository;
import com.proveyu.shared.error.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final ExamRepository examRepository;
    private final SkillRepository skillRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final InterviewInviteRepository interviewInviteRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(UserRepository userRepository,
                        ExamRepository examRepository,
                        SkillRepository skillRepository,
                        BookingRepository bookingRepository,
                        PaymentRepository paymentRepository,
                        InterviewInviteRepository interviewInviteRepository,
                        PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.examRepository = examRepository;
        this.skillRepository = skillRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.interviewInviteRepository = interviewInviteRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User createAdminUser(String email, String fullName, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new DomainException("Email is already registered", HttpStatus.BAD_REQUEST, "EMAIL_EXISTS");
        }

        User adminUser = User.builder()
                .email(email)
                .fullName(fullName)
                .passwordHash(passwordEncoder.encode(password))
                .role(Role.ADMIN)
                .active(true)
                .build();

        return userRepository.save(adminUser);
    }

    @Transactional
    public Exam createExam(UUID skillId, short level, String title, short durationMinutes, int totalMarks, int passingMarks, int priceCents, String currency) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new DomainException("Skill not found", HttpStatus.NOT_FOUND, "SKILL_NOT_FOUND"));

        Exam exam = new Exam(
                null,
                skill.getId(),
                level,
                title,
                durationMinutes > 0 ? durationMinutes : 60,
                totalMarks > 0 ? totalMarks : 100,
                passingMarks > 0 ? passingMarks : 40,
                priceCents,
                currency != null ? currency : "INR",
                true,
                null
        );

        return examRepository.save(exam);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPlatformStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("totalExams", examRepository.count());
        stats.put("totalBookings", bookingRepository.count());
        stats.put("totalPayments", paymentRepository.count());
        
        long placedCount = interviewInviteRepository.findAll().stream()
                .filter(i -> i.getPlacementStatus() == PlacementStatus.PLACED)
                .count();
        stats.put("totalPlacedCandidates", placedCount);

        return stats;
    }
}
