package com.saanjha.modules.chat.entity;

/** Pluggable attachment storage backend. Chat never touches bytes directly -
 * this only records which provider issued the storageReference a client
 * should resolve to an actual URL/blob. Adding a new provider is a new enum
 * constant + adapter, never a schema change. */
public enum StorageProvider {
    CLOUDINARY,
    S3
}
