package org.wodrol.brakoffpc.imports;

public class PdfImportException extends RuntimeException {

    public PdfImportException(String message) {
        super(message);
    }

    public PdfImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
