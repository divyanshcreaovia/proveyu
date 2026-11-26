import { slide } from '../lib.mjs';

export default [
  slide('Why it matters', 'Data that points to other data', [
    'Linked lists, trees and graphs model relationships: next song, parent folder, friend of a friend, road between cities.',
    'Product companies (Amazon, Microsoft, Flipkart) ask tree and graph questions in almost every interview loop.',
    'Two traversals, depth-first (DFS) and breadth-first (BFS), solve most of them.',
  ]),
  slide('Real-world use', 'Where you meet these structures', [
    'Linked lists: browser history, music playlists, LRU caches, undo chains.',
    'Trees: folders on disk, the HTML DOM, company org charts, database indexes (B-trees), autocomplete (tries).',
    'Graphs: Google Maps roads, LinkedIn connections ("2nd degree" is BFS), course prerequisites (topological order), network routing.',
    'Build tools (npm, Maven, Gradle) order dependencies with a topological sort and reject cycles.',
  ]),
  slide('Core idea', 'Nodes and edges', [
    'Linked list: each node has a value and a pointer to the next node. No index; walk from the head.',
    'Binary tree: each node has up to two children. Height decides the cost; a balanced tree has height log n.',
    'Graph: nodes plus edges, stored as an adjacency list (for each node, its neighbours).',
    'In these problems the input is given as text (level order for trees, edge pairs for graphs) and the starter builds the structure for you.',
  ]),
  slide('Syntax', 'Build an adjacency list', [
    'The most common graph setup: n nodes numbered 0 .. n − 1 and a list of edges [u, v].',
  ], {
    javascript: `const adj = Array.from({ length: n }, () => []);
for (const [u, v] of edges) {
  adj[u].push(v);
  adj[v].push(u);        // drop for a directed graph
}`,
    python: `adj = [[] for _ in range(n)]
for u, v in edges:
    adj[u].append(v)
    adj[v].append(u)     # drop for a directed graph`,
    java: `List<List<Integer>> adj = new ArrayList<>();
for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
for (int[] e : edges) {
    adj.get(e[0]).add(e[1]);
    adj.get(e[1]).add(e[0]);   // drop for a directed graph
}`,
    cpp: `vector<vector<int>> adj(n);
for (auto& e : edges) {
    adj[e[0]].push_back(e[1]);
    adj[e[1]].push_back(e[0]);   // drop for a directed graph
}`,
  }),
  slide('Pattern', 'BFS: shortest path in steps', [
    'Start from the source, visit all neighbours, then their neighbours: level by level with a queue.',
    'The first time BFS reaches a node is by the fewest edges. Mark nodes visited when you add them to the queue.',
    'Level-order traversal of a tree is the same idea.',
  ], {
    javascript: `const dist = new Array(n).fill(-1), q = [src];
dist[src] = 0;
for (let h = 0; h < q.length; h++) {
  const u = q[h];
  for (const v of adj[u]) if (dist[v] === -1) { dist[v] = dist[u] + 1; q.push(v); }
}`,
    python: `from collections import deque
dist = [-1] * n
dist[src] = 0
q = deque([src])
while q:
    u = q.popleft()
    for v in adj[u]:
        if dist[v] == -1:
            dist[v] = dist[u] + 1
            q.append(v)`,
    java: `int[] dist = new int[n];
Arrays.fill(dist, -1);
dist[src] = 0;
Deque<Integer> q = new ArrayDeque<>(List.of(src));
while (!q.isEmpty()) {
    int u = q.poll();
    for (int v : adj.get(u)) if (dist[v] == -1) { dist[v] = dist[u] + 1; q.add(v); }
}`,
    cpp: `vector<int> dist(n, -1);
dist[src] = 0;
queue<int> q; q.push(src);
while (!q.empty()) {
    int u = q.front(); q.pop();
    for (int v : adj[u]) if (dist[v] == -1) { dist[v] = dist[u] + 1; q.push(v); }
}`,
  }),
  slide('Pattern', 'DFS on trees: answer from the children', [
    'Most tree questions are "ask the left subtree, ask the right subtree, combine": height, diameter, path sums, balanced check.',
    'Linked lists: slow and fast pointers find the middle and detect cycles; reversing needs three pointers (prev, cur, next).',
  ], {
    javascript: `function height(node) {
  if (!node) return 0;
  return 1 + Math.max(height(node.left), height(node.right));
}`,
    python: `def height(node):
    if node is None:
        return 0
    return 1 + max(height(node.left), height(node.right))`,
    java: `static int height(Node node) {
    if (node == null) return 0;
    return 1 + Math.max(height(node.left), height(node.right));
}`,
    cpp: `int height(Node* node) {
    if (!node) return 0;
    return 1 + max(height(node->left), height(node->right));
}`,
  }),
  slide('Complexity', 'Visit everything once', [
    'BFS and DFS cost O(V + E): every node and edge is handled once.',
    'Tree operations cost O(height): log n when balanced, n when the tree is a chain.',
    'Weighted shortest path needs Dijkstra (a priority queue): O(E log V).',
  ]),
  slide('Common mistakes', 'What breaks pointer and graph code', [
    'Forgetting the visited mark: cycles make DFS or BFS run forever.',
    'Null children: check node before reading node.left; an empty tree is a valid input.',
    'Losing the rest of a linked list by changing next before saving it.',
    'Deep DFS recursion on a long chain can overflow the stack; use an explicit stack or BFS.',
  ]),
  slide('Going further', 'For advanced learners and interviews', [
    'Topological sort (Kahn: repeatedly remove nodes with no incoming edges) orders tasks and detects cycles.',
    'Union-Find (disjoint set) answers "are these connected?" almost in O(1); used in Kruskal minimum spanning tree.',
    'Binary search trees keep values sorted: in-order traversal gives sorted output; validate with min / max bounds.',
    'Next: mix these with DP (DP on trees, shortest paths) and you are ready for most interview rounds.',
  ]),
];
