import { q } from '../lib.mjs';
import guide from '../guides/lists-trees-graphs.mjs';

export default {
  key: 'lists-trees-graphs', title: 'Linked List, Trees, Graphs', summary: 'Reverse, cycle, BFS and DFS, shortest path, topological sort, union-find',
  guide,
  problems: [
    q('reverse-list', 'Reverse a linked list', 'E', 'values:ints', 'The list is given as its values from head to tail. Build or walk it and return the values reversed.', 'Keep prev, cur and next while relinking.',
      values => values.slice().reverse(), [['1 2 3 4 5', '5 4 3 2 1'], ['1 2', '2 1'], ['7', '7'], ['', ''], ['3 3 1', '1 3 3']]),
    q('has-cycle', 'Detect a cycle', 'E', 'next:ints, head:int', 'next[i] is the node after node i, or -1. Starting at head, return true if you ever come back to a visited node.', 'Slow moves one step, fast moves two.',
      (next, head) => { const step = i => i === -1 ? -1 : next[i]; let s = head, f = head; while (f !== -1 && step(f) !== -1) { s = step(s); f = step(step(f)); if (s === f) return true; } return false; },
      [['1 2 3 1\n0', 'true'], ['1 2 -1\n0', 'false'], ['0\n0', 'true'], ['-1\n0', 'false'], ['1 -1 3 2\n0', 'false']]),
    q('tree-depth', 'Maximum depth of a tree', 'E', 'tree:tree', 'The tree is given in level order. Return the number of nodes on the longest path from the root down.', 'Process level by level, or recurse: 1 + max(left, right).',
      tree => { if (!tree.length || tree[0] === null) return 0; let level = 1, i = 1, depth = 0; while (level > 0) { depth++; let next = 0; for (let k = 0; k < level; k++) { for (let c = 0; c < 2 && i < tree.length; c++, i++) if (tree[i] !== null) next++; } level = next; } return depth; },
      [['3 9 20 null null 15 7', '3'], ['1 null 2', '2'], ['', '0'], ['5', '1'], ['1 2 null 3 null 4', '4']]),
    q('level-order', 'Level order traversal', 'M', 'tree:tree', 'The tree is given in level order with "null" markers. Return the values grouped by level, one level per line (return an array of arrays).', 'BFS: handle one whole level at a time.',
      tree => { if (!tree.length || tree[0] === null) return []; const out = []; let level = [tree[0]], i = 1; while (level.length) { out.push(level); const next = []; for (let k = 0; k < level.length; k++) for (let c = 0; c < 2 && i < tree.length; c++, i++) if (tree[i] !== null) next.push(tree[i]); level = next; } return out; },
      [['3 9 20 null null 15 7', '3\n9 20\n15 7'], ['1', '1'], ['1 2 3 4 5 6 7', '1\n2 3\n4 5 6 7'], ['1 null 2 3', '1\n2\n3'], ['1 2 null 3', '1\n2\n3']]),
    q('connected-components', 'Count connected groups', 'M', 'n:int, edges:rows', 'An undirected graph has nodes 0..n-1. Each edge line is "u v". Return the number of connected groups.', 'Union-find, or DFS from every unvisited node.',
      (n, edges) => { const p = Array.from({ length: n }, (_, i) => i); const find = x => p[x] === x ? x : (p[x] = find(p[x])); let groups = n; for (const [u, v] of edges) { const a = find(u), b = find(v); if (a !== b) { p[a] = b; groups--; } } return groups; },
      [['5\n3\n0 1\n1 2\n3 4', '2'], ['4\n0', '4'], ['3\n3\n0 1\n1 2\n2 0', '1'], ['1\n0', '1'], ['6\n3\n0 1\n2 3\n4 5', '3']]),
    q('shortest-path', 'Shortest path (unweighted)', 'M', 'n:int, edges:rows, start:int, end:int', 'In an undirected graph with nodes 0..n-1, return the fewest edges from start to end, or -1.', 'BFS from start; the first visit is the shortest.',
      (n, edges, start, end) => { const g = Array.from({ length: n }, () => []); for (const [u, v] of edges) { g[u].push(v); g[v].push(u); } const d = new Array(n).fill(-1); d[start] = 0; const queue = [start]; for (let h = 0; h < queue.length; h++) for (const v of g[queue[h]]) if (d[v] === -1) { d[v] = d[queue[h]] + 1; queue.push(v); } return d[end]; },
      [['4\n4\n0 1\n1 2\n2 3\n0 2\n0\n3', '2'], ['3\n1\n0 1\n0\n2', '-1'], ['1\n0\n0\n0', '0'], ['5\n4\n0 1\n1 2\n2 3\n3 4\n4\n0', '4'], ['6\n6\n0 1\n0 2\n1 3\n2 4\n3 5\n4 5\n0\n5', '3']]),
    q('topological-order', 'Course schedule order', 'H', 'n:int, prerequisites:rows', 'Courses are 0..n-1. A line "a b" means course a must be done before b. Return one order that finishes all, always taking the smallest available course; return [-1] if there is a cycle.', "Kahn's algorithm with the smallest available node first.",
      (n, prerequisites) => { const g = Array.from({ length: n }, () => []), deg = new Array(n).fill(0); for (const [a, b] of prerequisites) { g[a].push(b); deg[b]++; } const ready = []; for (let i = 0; i < n; i++) if (!deg[i]) ready.push(i); const out = []; while (ready.length) { ready.sort((x, y) => x - y); const u = ready.shift(); out.push(u); for (const v of g[u]) if (--deg[v] === 0) ready.push(v); } return out.length === n ? out : [-1]; },
      [['4\n3\n0 1\n0 2\n1 3', '0 1 2 3'], ['2\n2\n0 1\n1 0', '-1'], ['3\n0', '0 1 2'], ['3\n2\n2 0\n0 1', '2 0 1'], ['6\n6\n5 2\n5 0\n4 0\n4 1\n2 3\n3 1', '4 5 0 2 3 1']]),
    q('redundant-edge', 'Redundant connection', 'H', 'edges:rows', 'A tree with nodes 1..n got one extra edge. The edges are listed one per line. Return the last listed edge that closes a cycle.', 'Union-find: the edge whose ends are already joined.',
      edges => { const p = []; const find = x => { if (p[x] === undefined) p[x] = x; return p[x] === x ? x : (p[x] = find(p[x])); }; let ans = []; for (const [u, v] of edges) { const a = find(u), b = find(v); if (a === b) ans = [u, v]; else p[a] = b; } return ans; },
      [['3\n1 2\n1 3\n2 3', '2 3'], ['5\n1 2\n2 3\n3 4\n1 4\n1 5', '1 4'], ['3\n1 2\n2 3\n3 1', '3 1'], ['4\n1 2\n2 3\n2 4\n4 3', '4 3'], ['6\n1 2\n1 3\n3 4\n4 5\n5 6\n2 6', '2 6']]),
  ],
};
