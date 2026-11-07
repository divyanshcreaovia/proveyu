package com.proveyu.placement.application.dto;

import java.util.List;
import java.util.Map;

public class CollegeDashboardDto {
    private long totalStudents;
    private long placedStudents;
    private long unplacedStudents;
    private double placementPercentage;
    private long activeDrives;
    private double averagePackageLpa;
    private double highestPackageLpa;
    private Map<String, Long> branchWisePlacements;
    private List<StudentProfileDto> recentPlacedStudents;
    private List<PlacementDriveDto.Response> upcomingDrives;

    public CollegeDashboardDto() {}

    public long getTotalStudents() { return totalStudents; }
    public void setTotalStudents(long totalStudents) { this.totalStudents = totalStudents; }

    public long getPlacedStudents() { return placedStudents; }
    public void setPlacedStudents(long placedStudents) { this.placedStudents = placedStudents; }

    public long getUnplacedStudents() { return unplacedStudents; }
    public void setUnplacedStudents(long unplacedStudents) { this.unplacedStudents = unplacedStudents; }

    public double getPlacementPercentage() { return placementPercentage; }
    public void setPlacementPercentage(double placementPercentage) { this.placementPercentage = placementPercentage; }

    public long getActiveDrives() { return activeDrives; }
    public void setActiveDrives(long activeDrives) { this.activeDrives = activeDrives; }

    public double getAveragePackageLpa() { return averagePackageLpa; }
    public void setAveragePackageLpa(double averagePackageLpa) { this.averagePackageLpa = averagePackageLpa; }

    public double getHighestPackageLpa() { return highestPackageLpa; }
    public void setHighestPackageLpa(double highestPackageLpa) { this.highestPackageLpa = highestPackageLpa; }

    public Map<String, Long> getBranchWisePlacements() { return branchWisePlacements; }
    public void setBranchWisePlacements(Map<String, Long> branchWisePlacements) { this.branchWisePlacements = branchWisePlacements; }

    public List<StudentProfileDto> getRecentPlacedStudents() { return recentPlacedStudents; }
    public void setRecentPlacedStudents(List<StudentProfileDto> recentPlacedStudents) { this.recentPlacedStudents = recentPlacedStudents; }

    public List<PlacementDriveDto.Response> getUpcomingDrives() { return upcomingDrives; }
    public void setUpcomingDrives(List<PlacementDriveDto.Response> upcomingDrives) { this.upcomingDrives = upcomingDrives; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final CollegeDashboardDto dto = new CollegeDashboardDto();

        public Builder totalStudents(long val) { dto.setTotalStudents(val); return this; }
        public Builder placedStudents(long val) { dto.setPlacedStudents(val); return this; }
        public Builder unplacedStudents(long val) { dto.setUnplacedStudents(val); return this; }
        public Builder placementPercentage(double val) { dto.setPlacementPercentage(val); return this; }
        public Builder activeDrives(long val) { dto.setActiveDrives(val); return this; }
        public Builder averagePackageLpa(double val) { dto.setAveragePackageLpa(val); return this; }
        public Builder highestPackageLpa(double val) { dto.setHighestPackageLpa(val); return this; }
        public Builder branchWisePlacements(Map<String, Long> val) { dto.setBranchWisePlacements(val); return this; }
        public Builder recentPlacedStudents(List<StudentProfileDto> val) { dto.setRecentPlacedStudents(val); return this; }
        public Builder upcomingDrives(List<PlacementDriveDto.Response> val) { dto.setUpcomingDrives(val); return this; }

        public CollegeDashboardDto build() { return dto; }
    }
}
