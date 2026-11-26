static int solve(int[] weights, int[] values, int capacity) {
    int[] dp = new int[capacity + 1];
    for (int i = 0; i < weights.length; i++)
        for (int c = capacity; c >= weights[i]; c--) dp[c] = Math.max(dp[c], dp[c - weights[i]] + values[i]);
    return dp[capacity];
}
