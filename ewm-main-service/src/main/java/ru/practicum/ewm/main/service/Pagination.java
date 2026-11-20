package ru.practicum.ewm.main.service;

import java.util.List;

final class Pagination {
    private Pagination() {
    }

    static <T> List<T> slice(List<T> list, int from, int size) {
        if (from >= list.size())
            return List.of();
        return list.subList(from, from + Math.min(size, list.size() - from));
    }
}
