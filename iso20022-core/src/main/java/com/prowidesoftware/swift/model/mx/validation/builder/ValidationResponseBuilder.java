package com.prowidesoftware.swift.model.mx.validation.builder;

import com.fss.iso20022.api.ErrorCategory;
import com.fss.iso20022.api.FssError;
import com.fss.iso20022.api.ResponseStatus;
import com.fss.iso20022.api.ValidationResponse;
import com.prowidesoftware.swift.model.mx.validation.ISOParserResult;
import com.prowidesoftware.swift.model.mx.validation.ValidationError;
import com.prowidesoftware.swift.model.mx.validation.semantic.SemanticValidationResult;
import com.prowidesoftware.swift.model.mx.validation.semantic.SemanticViolation;
import com.prowidesoftware.swift.model.mx.validation.semantic.TechnicalEvaluationError;
import java.util.ArrayList;
import java.util.List;

/**
 * Mapper responsible for converting the internal orchestration result (ISOParserResult)
 * into the public, Prowide-independent contract (ValidationResponse).
 *
 * This builder performs NO validation itself; it only maps existing statuses and errors.
 */
public final class ValidationResponseBuilder {

    private ValidationResponseBuilder() {
        // Prevent instantiation
    }

    public static ValidationResponse build(ISOParserResult result) {
        return build(result, result != null ? result.getSemanticResult() : null);
    }

    public static ValidationResponse build(ISOParserResult result, SemanticValidationResult semanticResult) {
        String messageType =
                result.getIdentifier() != null ? result.getIdentifier().getFullMessageType() : null;
        String businessMessageId = result.getBusinessMessageId();
        List<FssError> errors = new ArrayList<>();
        ResponseStatus status = determineStatusAndMapErrors(result, semanticResult, errors);

        return new ValidationResponse(messageType, businessMessageId, status, errors);
    }

    private static ResponseStatus determineStatusAndMapErrors(
            ISOParserResult result, SemanticValidationResult semanticResult, List<FssError> errors) {
        // 1. Unidentifiable Input (Hard Technical Failure)
        if (result.getSchemaStatus() == ISOParserResult.SchemaValidationStatus.UNIDENTIFIABLE_INPUT) {
            errors.add(new FssError(
                    ErrorCategory.TECHNICAL,
                    "UNIDENTIFIABLE_INPUT",
                    "Could not identify ISO 20022 message type from namespace",
                    null,
                    "FATAL"));
            return ResponseStatus.REJECTED;
        }

        // 2. Global Technical Errors (e.g. schema compilation crash)
        // Checked early because an infrastructure crash overrides standard validation statuses.
        if (result.getTechnicalError() != null) {
            errors.add(
                    new FssError(ErrorCategory.TECHNICAL, "SYSTEM_ERROR", result.getTechnicalError(), null, "FATAL"));
            return ResponseStatus.SYSTEM_ERROR;
        }

        // 3. Schema Not Found (Schema is unavailable - cannot mark as valid)
        if (result.getSchemaStatus() == ISOParserResult.SchemaValidationStatus.SCHEMA_NOT_FOUND) {
            errors.add(new FssError(
                    ErrorCategory.TECHNICAL,
                    "SCHEMA_NOT_FOUND",
                    "No XSD registered for message type: "
                            + (result.getIdentifier() != null
                                    ? result.getIdentifier().getFullMessageType()
                                    : "unknown"),
                    null,
                    "FATAL"));
            return ResponseStatus.SCHEMA_UNAVAILABLE;
        }

        // 4. XSD Validation Failures
        if (result.getSchemaStatus() == ISOParserResult.SchemaValidationStatus.FAIL) {
            for (ValidationError ve : result.getValidationErrors()) {
                String loc = ve.getLine() > 0 ? "Line: " + ve.getLine() + ", Column: " + ve.getColumn() : null;
                errors.add(new FssError(
                        ErrorCategory.STRUCTURAL, "XSD_VIOLATION", ve.getMessage(), loc, ve.getSeverity()));
            }
            return ResponseStatus.REJECTED;
        }

        // 5. Model Parsing Failures
        if (result.getModelStatus() == ISOParserResult.ModelParsingStatus.PARSE_ERROR
                || result.getModelDetail() == ISOParserResult.ModelParsingDetail.PARSE_ERROR) {
            errors.add(new FssError(
                    ErrorCategory.TECHNICAL,
                    "PARSE_ERROR",
                    "Prowide JAXB model failed to parse XML message",
                    null,
                    "FATAL"));
            return ResponseStatus.SYSTEM_ERROR;
        }

        if (result.getModelStatus() == ISOParserResult.ModelParsingStatus.MODEL_UNAVAILABLE
                || result.getModelStatus()
                        == ISOParserResult.ModelParsingStatus.PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR
                || result.getModelDetail() == ISOParserResult.ModelParsingDetail.MODEL_UNAVAILABLE) {
            errors.add(new FssError(
                    ErrorCategory.TECHNICAL,
                    "MODEL_UNAVAILABLE",
                    "Prowide JAXB model unavailable for message type: "
                            + (result.getIdentifier() != null
                                    ? result.getIdentifier().getFullMessageType()
                                    : "unknown"),
                    null,
                    "FATAL"));
            return ResponseStatus.SYSTEM_ERROR;
        }

        // 6. Semantic Validation Results
        if (semanticResult != null) {
            // Semantic Technical Errors
            for (TechnicalEvaluationError tee : semanticResult.getTechnicalErrors()) {
                errors.add(new FssError(
                        ErrorCategory.TECHNICAL,
                        "SEMANTIC_TECH_ERROR",
                        tee.getErrorMessage(),
                        tee.getRuleId(),
                        "FATAL"));
            }

            // Semantic Violations
            for (SemanticViolation sv : semanticResult.getViolations()) {
                errors.add(new FssError(
                        ErrorCategory.SEMANTIC, sv.getRuleId(), sv.getMessage(), sv.getErrorPath(), sv.getSeverity()));
            }

            if (!semanticResult.getTechnicalErrors().isEmpty()) {
                return ResponseStatus.SYSTEM_ERROR;
            }
            if (semanticResult.hasErrors()) {
                return ResponseStatus.REJECTED;
            }
        }

        // Propagate structural warnings (such as BAH test fixture warning or schema warnings)
        for (ValidationError ve : result.getValidationErrors()) {
            if ("WARNING".equalsIgnoreCase(ve.getSeverity()) || "WARN".equalsIgnoreCase(ve.getSeverity())) {
                String loc = ve.getLine() > 0 ? "Line: " + ve.getLine() + ", Column: " + ve.getColumn() : null;
                errors.add(new FssError(ErrorCategory.STRUCTURAL, "XSD_WARNING", ve.getMessage(), loc, "WARNING"));
            }
        }

        // If we reach here, no hard rejections, missing schema, or fatal system errors occurred.
        return ResponseStatus.VALID;
    }
}
