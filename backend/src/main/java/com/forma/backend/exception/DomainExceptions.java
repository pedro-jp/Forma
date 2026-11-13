package com.forma.backend.exception;

public final class DomainExceptions {

    private DomainExceptions() {}

    public static class UnauthorizedAccessException extends RuntimeException {
        public UnauthorizedAccessException(String message) {
            super(message);
        }
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException(String message) {
            super(message);
        }
    }

    public static class AccountDeletedException extends RuntimeException {
        public AccountDeletedException(String message) {
            super(message);
        }
    }

    public static class AccountPausedException extends RuntimeException {
        public AccountPausedException(String message) {
            super(message);
        }
    }

    public static class AccountAlreadyExistsException extends RuntimeException {
        private final String email;

        public AccountAlreadyExistsException(String message, String email, String ignoredName) {
            super(message);
            this.email = email;
        }

        public String getEmail() {
            return email;
        }
    }

    public static class AiBlockedException extends RuntimeException {
        private final boolean globalBlock;

        public AiBlockedException(String message, boolean globalBlock) {
            super(message);
            this.globalBlock = globalBlock;
        }

        public boolean isGlobalBlock() {
            return globalBlock;
        }
    }

    public static class AiConsentRequiredException extends RuntimeException {
        public AiConsentRequiredException(String message) {
            super(message);
        }
    }

    public static class AiQuotaExceededException extends RuntimeException {
        public AiQuotaExceededException(String message) {
            super(message);
        }
    }

    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }

    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }

    public static class AiUpstreamException extends RuntimeException {
        private final String internalDetail;

        public AiUpstreamException(String userFriendlyMessage, String internalDetail, Throwable cause) {
            super(userFriendlyMessage, cause);
            this.internalDetail = internalDetail;
        }

        public String getInternalDetail() {
            return internalDetail;
        }
    }
}
