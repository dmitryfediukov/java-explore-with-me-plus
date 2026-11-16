package ru.practicum.ewm.main.repository;

import org.springframework.data.domain.AbstractPageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class OffsetPageRequest extends AbstractPageRequest {
    private final long offset;
    private final Sort sort;

    public OffsetPageRequest(int offset, int size, Sort sort) {
        super(0, size);
        if (offset < 0)
            throw new IllegalArgumentException("offset must not be negative");
        this.offset = offset;
        this.sort = sort;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public Pageable next() {
        return new OffsetPageRequest((int) (offset + getPageSize()), getPageSize(), sort);
    }

    @Override
    public Pageable previousOrFirst() {
        return hasPrevious() ? new OffsetPageRequest((int) Math.max(0, offset - getPageSize()), getPageSize(), sort)
                : first();
    }

    @Override
    public Pageable previous() {
        return previousOrFirst();
    }

    @Override
    public Pageable first() {
        return new OffsetPageRequest(0, getPageSize(), sort);
    }

    @Override
    public Pageable withPage(int pageNumber) {
        return new OffsetPageRequest(pageNumber * getPageSize(), getPageSize(), sort);
    }

    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }
}
