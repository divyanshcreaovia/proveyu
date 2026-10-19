package com.proveyu.placement.infrastructure;

import com.proveyu.placement.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {
    List<Student> findByCollegeId(UUID collegeId);
    Optional<Student> findByCollegeIdAndStudentId(UUID collegeId, UUID studentId);
    Optional<Student> findByStudentId(UUID studentId);
    Optional<Student> findByCollegeIdAndRollNumber(UUID collegeId, String rollNumber);
}
