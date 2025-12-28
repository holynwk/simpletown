package com.simpletown.map;

import com.simpletown.data.ChunkPosition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builds outer outlines for a set of chunk-aligned squares (16x16 blocks) without internal edges.
 */
public final class ChunkOutlineBuilder {

    private static final double CHUNK_SIZE = 16.0;

    private ChunkOutlineBuilder() {
    }

    public static List<List<Point>> buildOutlines(Set<ChunkPosition> chunks) {
        Set<Edge> edges = new HashSet<>();
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        for (ChunkPosition pos : chunks) {
            double x0 = pos.getX() * CHUNK_SIZE;
            double x1 = x0 + CHUNK_SIZE;
            double z0 = pos.getZ() * CHUNK_SIZE;
            double z1 = z0 + CHUNK_SIZE;
            Point p1 = new Point(x0, z0);
            Point p2 = new Point(x1, z0);
            Point p3 = new Point(x1, z1);
            Point p4 = new Point(x0, z1);
            toggleEdge(edges, p1, p2);
            toggleEdge(edges, p2, p3);
            toggleEdge(edges, p3, p4);
            toggleEdge(edges, p4, p1);
        }

        Map<Point, Set<Point>> adjacency = buildAdjacency(edges);
        return buildLoops(edges, adjacency);
    }

    private static void toggleEdge(Set<Edge> edges, Point a, Point b) {
        Edge edge = new Edge(a, b);
        if (!edges.add(edge)) {
            edges.remove(edge);
        }
    }

    private static Map<Point, Set<Point>> buildAdjacency(Set<Edge> edges) {
        Map<Point, Set<Point>> adjacency = new HashMap<>();
        for (Edge edge : edges) {
            adjacency.computeIfAbsent(edge.a(), k -> new HashSet<>()).add(edge.b());
            adjacency.computeIfAbsent(edge.b(), k -> new HashSet<>()).add(edge.a());
        }
        return adjacency;
    }

    private static List<List<Point>> buildLoops(Set<Edge> edges, Map<Point, Set<Point>> adjacency) {
        Set<Edge> remaining = new HashSet<>(edges);
        Map<Point, Set<Edge>> incident = new HashMap<>();
        for (Edge edge : edges) {
            incident.computeIfAbsent(edge.a(), k -> new HashSet<>()).add(edge);
            incident.computeIfAbsent(edge.b(), k -> new HashSet<>()).add(edge);
        }

        List<List<Point>> loops = new ArrayList<>();
        while (!remaining.isEmpty()) {
            Edge start = remaining.iterator().next();
            Point startPoint = start.a();
            Point nextPoint = start.b();
            List<Point> loop = new ArrayList<>();
            loop.add(startPoint);
            remaining.remove(start);

            Point previous = startPoint;
            Point current = nextPoint;
            while (true) {
                loop.add(current);
                Edge nextEdge = pickNextEdge(previous, current, incident, remaining);
                if (nextEdge == null) {
                    break;
                }
                remaining.remove(nextEdge);
                Point candidate = nextEdge.other(current);
                previous = current;
                current = candidate;
                if (current.equals(startPoint)) {
                    loop.add(startPoint);
                    break;
                }
            }
            if (loop.size() >= 4 && loop.get(0).equals(loop.get(loop.size() - 1))) {
                loops.add(loop);
            }
        }
        return loops;
    }

    private static Edge pickNextEdge(Point previous, Point current, Map<Point, Set<Edge>> incident, Set<Edge> remaining) {
        Set<Edge> candidates = incident.getOrDefault(current, Set.of());
        List<DirectedEdge> directed = new ArrayList<>();
        for (Edge edge : candidates) {
            if (!remaining.contains(edge)) {
                continue;
            }
            Point other = edge.other(current);
            directed.add(new DirectedEdge(edge, directionIndex(current, other)));
        }
        if (directed.isEmpty()) {
            return null;
        }

        Integer incomingDir = previous == null ? null : directionIndex(previous, current);
        directed.sort((a, b) -> compareByTurn(a.direction(), b.direction(), incomingDir));
        return directed.get(0).edge();
    }

    private static int compareByTurn(int dirA, int dirB, Integer incoming) {
        if (incoming == null) {
            return Integer.compare(dirA, dirB);
        }
        int[] preference = new int[]{(incoming + 3) % 4, incoming, (incoming + 1) % 4, (incoming + 2) % 4};
        return Integer.compare(indexOf(preference, dirA), indexOf(preference, dirB));
    }

    private static int indexOf(int[] arr, int value) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == value) {
                return i;
            }
        }
        return arr.length;
    }

    private static int directionIndex(Point from, Point to) {
        double dx = to.x() - from.x();
        double dz = to.z() - from.z();
        if (dx > 0) {
            return 0; // East
        }
        if (dz > 0) {
            return 1; // South
        }
        if (dx < 0) {
            return 2; // West
        }
        return 3; // North
    }

    public record Point(double x, double z) implements Comparable<Point> {
        @Override
        public int compareTo(Point other) {
            int cmp = Double.compare(this.x, other.x);
            if (cmp != 0) {
                return cmp;
            }
            return Double.compare(this.z, other.z);
        }
    }

    private record Edge(Point a, Point b) {
        Edge {
            if (a == null || b == null) {
                throw new IllegalArgumentException("Edge points cannot be null");
            }
            if (b.compareTo(a) < 0) {
                Point tmp = a;
                a = b;
                b = tmp;
            }
        }

        Point other(Point point) {
            if (a.equals(point)) {
                return b;
            }
            if (b.equals(point)) {
                return a;
            }
            return point;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Edge edge = (Edge) o;
            return Objects.equals(a, edge.a) && Objects.equals(b, edge.b);
        }

        @Override
        public int hashCode() {
            return Objects.hash(a, b);
        }
    }

    private record DirectedEdge(Edge edge, int direction) {
    }
}