package dev.bookreports.config;

public record MySqlSettings(String host, int port, String database, String user, String password, int poolSize) {
}
