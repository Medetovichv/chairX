package kg.chairx.security.application;

public record CreateUserCommand(
        String username,
        String password,
        String displayName
) {
}