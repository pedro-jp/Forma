//
//  KeychainHelper.swift
//  Forma
//
//  Created by João Pedro on 11/02/26.
//

import Foundation
import Security

public final class KeychainHelper {
    public static let shared = KeychainHelper()
    private let serviceName = "com.forma.app.auth"
    
    private init() {}
    
    @discardableResult
    public func save(key: String, string: String) -> Bool {
        guard let data = string.data(using: .utf8) else { return false }
        return save(key: key, data: data)
    }
    
    @discardableResult
    public func save(key: String, data: Data) -> Bool {
        // Remove item anterior se já existir
        delete(key: key)
        
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        
        let status = SecItemAdd(query as CFDictionary, nil)
        UserDefaults.standard.removeObject(forKey: "kc_fallback_\(key)")
        return status == errSecSuccess
    }
    
    public func loadString(key: String) -> String? {
        guard let data = load(key: key) else { return nil }
        return String(data: data, encoding: .utf8)
    }
    
    public func load(key: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        
        var dataTypeRef: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &dataTypeRef)
        
        if status == errSecSuccess, let data = dataTypeRef as? Data {
            return data
        }
        
        // Nunca armazena nem lê tokens/segredos em UserDefaults por segurança (LGPD Art. 46)
        UserDefaults.standard.removeObject(forKey: "kc_fallback_\(key)")
        return nil
    }
    
    @discardableResult
    public func delete(key: String) -> Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key
        ]
        let status = SecItemDelete(query as CFDictionary)
        UserDefaults.standard.removeObject(forKey: "kc_fallback_\(key)")
        return status == errSecSuccess || status == errSecItemNotFound
    }
}
