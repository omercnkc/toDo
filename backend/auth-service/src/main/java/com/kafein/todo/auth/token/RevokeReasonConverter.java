package com.kafein.todo.auth.token;

import com.kafein.todo.common.persistence.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter
public class RevokeReasonConverter extends CodedEnumConverter<RevokeReason> {

    public RevokeReasonConverter() {
        super(RevokeReason.class);
    }
}
