package com.proveyu.placement.application.dto;

import java.util.ArrayList;
import java.util.List;

public class BulkUploadResultDto {
    private int totalRows;
    private int importedCount;
    private int updatedCount;
    private int failedCount;
    private List<String> errors = new ArrayList<>();
    private List<StudentProfileDto> importedStudents = new ArrayList<>();

    public BulkUploadResultDto() {}

    public BulkUploadResultDto(int totalRows, int importedCount, int updatedCount, int failedCount,
                               List<String> errors, List<StudentProfileDto> importedStudents) {
        this.totalRows = totalRows;
        this.importedCount = importedCount;
        this.updatedCount = updatedCount;
        this.failedCount = failedCount;
        this.errors = errors != null ? errors : new ArrayList<>();
        this.importedStudents = importedStudents != null ? importedStudents : new ArrayList<>();
    }

    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }

    public int getImportedCount() { return importedCount; }
    public void setImportedCount(int importedCount) { this.importedCount = importedCount; }

    public int getUpdatedCount() { return updatedCount; }
    public void setUpdatedCount(int updatedCount) { this.updatedCount = updatedCount; }

    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }

    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }

    public List<StudentProfileDto> getImportedStudents() { return importedStudents; }
    public void setImportedStudents(List<StudentProfileDto> importedStudents) { this.importedStudents = importedStudents; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private int totalRows;
        private int importedCount;
        private int updatedCount;
        private int failedCount;
        private List<String> errors = new ArrayList<>();
        private List<StudentProfileDto> importedStudents = new ArrayList<>();

        public Builder totalRows(int totalRows) { this.totalRows = totalRows; return this; }
        public Builder importedCount(int importedCount) { this.importedCount = importedCount; return this; }
        public Builder updatedCount(int updatedCount) { this.updatedCount = updatedCount; return this; }
        public Builder failedCount(int failedCount) { this.failedCount = failedCount; return this; }
        public Builder errors(List<String> errors) { this.errors = errors; return this; }
        public Builder importedStudents(List<StudentProfileDto> importedStudents) { this.importedStudents = importedStudents; return this; }

        public BulkUploadResultDto build() {
            return new BulkUploadResultDto(totalRows, importedCount, updatedCount, failedCount, errors, importedStudents);
        }
    }
}
