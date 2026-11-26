vector<int> solve(vector<int> nums, int target) {
    int l = 0, r = (int)nums.size() - 1;
    while (l < r) {
        int s = nums[l] + nums[r];
        if (s == target) return {l + 1, r + 1};
        if (s < target) l++; else r--;
    }
    return {-1};
}
