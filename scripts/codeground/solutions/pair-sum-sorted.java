static int[] solve(int[] nums, int target) {
    int l = 0, r = nums.length - 1;
    while (l < r) {
        int s = nums[l] + nums[r];
        if (s == target) return new int[] {l + 1, r + 1};
        if (s < target) l++; else r--;
    }
    return new int[] {-1};
}
