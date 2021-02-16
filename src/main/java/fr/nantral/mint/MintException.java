package fr.nantral.mint;

/** Mint-related exceptions for assumptions et al. */
public class MintException extends Exception {
    public MintException(String message) {
        super(message);
    }

    public MintException(Throwable cause) {
        super(cause);
    }

    public MintException(String message, Throwable cause) {
        super(message, cause);
    }
}
