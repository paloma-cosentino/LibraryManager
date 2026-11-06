package org.example.http

import org.example.model.Role

// Deserialized via Body.auto<RoleChangeRequest>(); an unknown role name fails the lens -> 400.
data class RoleChangeRequest(val role: Role)
