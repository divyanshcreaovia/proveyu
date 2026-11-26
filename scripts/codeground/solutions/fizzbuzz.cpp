vector<string> solve(int n) {
    vector<string> out;
    for (int i = 1; i <= n; i++) out.push_back(i % 15 == 0 ? "FizzBuzz" : i % 3 == 0 ? "Fizz" : i % 5 == 0 ? "Buzz" : to_string(i));
    return out;
}
