package biocompass.pkb.authz

import rego.v1

default allow := false

allowed_purposes := {"care", "self", "operations", "development"}
allowed_owner_actions := {"read", "write", "search"}
cross_user_read_actions := {"read", "search"}

allow if {
    input.actor.roles[_] == "pkb_admin"
}

allow if {
    input.actor.scopes[_] == "pkb:read:any"
    input.action in cross_user_read_actions
}

allow if {
    input.actor.user_id == input.resource.owner_user_id
    input.action in allowed_owner_actions
    input.purpose in allowed_purposes
    not restricted_resource
}

redactions contains "payload" if {
    not allow
}

restricted_resource if {
    input.resource.privacy_scope == "restricted"
}

restricted_resource if {
    input.resource.privacy_scope[_] == "restricted"
}
