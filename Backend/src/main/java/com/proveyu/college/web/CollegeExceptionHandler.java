package com.proveyu.college.web;

import com.proveyu.college.application.CampusInterviewService;
import com.proveyu.college.application.dto.DriveDtos.Conflict;
import com.proveyu.shared.response.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class CollegeExceptionHandler {

    /** Scheduling conflicts carry the list of clashing slots in "data" so the UI can show them. */
    @ExceptionHandler(CampusInterviewService.ConflictException.class)
    public ResponseEntity<ApiResponse<List<Conflict>>> conflicts(CampusInterviewService.ConflictException ex) {
        ApiResponse<List<Conflict>> body = ApiResponse.error(HttpStatus.CONFLICT.value(), ex.getMessage(), ex.getErrorCode(), null);
        body.setData(ex.getConflicts());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
