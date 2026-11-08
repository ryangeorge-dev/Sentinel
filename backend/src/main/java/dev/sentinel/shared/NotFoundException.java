package dev.sentinel.shared;

public class NotFoundException extends RuntimeException {
    public NotFoundException() { super("Monitor not found"); }
}
