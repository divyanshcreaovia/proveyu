import heapq


def solve(n, prerequisites):
    g = [[] for _ in range(n)]
    deg = [0] * n
    for a, b in prerequisites:
        g[a].append(b)
        deg[b] += 1
    ready = [i for i in range(n) if deg[i] == 0]
    heapq.heapify(ready)
    out = []
    while ready:
        u = heapq.heappop(ready)
        out.append(u)
        for v in g[u]:
            deg[v] -= 1
            if deg[v] == 0:
                heapq.heappush(ready, v)
    return out if len(out) == n else [-1]
