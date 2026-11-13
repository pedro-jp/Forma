package com.forma.backend.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter transparente que persiste colunas sensíveis com AES-256-GCM ("ENC:v1:...")
 * e devolve o texto plano para a camada de domínio.
 */
@Converter
public class EncryptedStringAttributeConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }
        FieldEncryptionService service = FieldEncryptionService.current();
        if (service == null) {
            return attribute;
        }
        return service.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }
        FieldEncryptionService service = FieldEncryptionService.current();
        if (service == null) {
            return dbData;
        }
        return service.decrypt(dbData);
    }
}
