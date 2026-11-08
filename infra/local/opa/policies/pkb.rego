package biocompass.pkb.authz

import rego.v1

default allow := false

decision := {
    "allowed": allow,
    "decision_reference": sprintf("local-opa:%s", [input.command_id]),
    "action": input.action,
    "target_user_id": input.resource.target_user_id,
    "obligations": {},
}

allowed_purposes := {"care", "self", "operations", "development"}
allowed_owner_actions := {"read", "write", "search"}
cross_user_read_actions := {"read", "search"}

# Disposable AU contract fixture only; this is not a production policy deployment.
source_record_permissions := {
    "create": "hydration:intake:create",
    "read": "hydration:intake:read",
    "aggregate": "hydration:intake:read",
    "delete": "hydration:intake:delete",
}

allow if {
    input.resource.command_type == "source_record"
    input.producer_service == "biocompass-watertracker-service"
    input.resource.attributes.source_system == input.producer_service
    input.actor.user_id == input.resource.target_user_id
    input.actor.purpose_of_use == "self"
    input.actor.scopes[_] == source_record_permissions[input.action]
    input.resource.attributes.entity_type == "nutrition_intake"
    input.resource.attributes.subtype == "water"
    not restricted_resource
    count(input.resource.consent_scope) == 0
}

# Authorized blind deletion reveals neither ownership nor existence of a target.
allow if {
    input.resource.command_type == "source_record"
    input.producer_service == "biocompass-watertracker-service"
    input.resource.attributes.source_system == input.producer_service
    input.actor.user_id == input.resource.target_user_id
    input.actor.purpose_of_use == "self"
    input.action == "delete"
    input.actor.scopes[_] == "hydration:intake:delete"
    input.resource.attributes.entity_type == ""
    input.resource.attributes.subtype == ""
}

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

allow if {
    input.actor.user_id == input.resource.target_user_id
    input.action == "write"
    input.actor.purpose_of_use in allowed_purposes
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
