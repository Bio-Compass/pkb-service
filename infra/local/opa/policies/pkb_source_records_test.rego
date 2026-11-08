package biocompass.pkb.authz

import rego.v1

record_input(action, scopes, entity, subtype, source) := {
    "command_id": "fixture-command",
    "action": action,
    "producer_service": "biocompass-watertracker-service",
    "actor": {"actor_id": "fixture-user", "user_id": "fixture-user", "roles": [], "scopes": scopes, "purpose_of_use": "self"},
    "resource": {
        "command_type": "source_record", "target_user_id": "fixture-user",
        "attributes": {"source_system": source, "entity_type": entity, "subtype": subtype},
        "privacy_scope": ["nutrition", "health"], "consent_scope": [],
    },
}

test_record_operations_require_their_own_permission if {
    every action, permission in source_record_permissions {
        allow with input as record_input(action, [permission], "nutrition_intake", "water", "biocompass-watertracker-service")
    }
}

test_summary_permission_does_not_read_raw_records if {
    not allow with input as record_input("read", ["hydration:summary:read"], "nutrition_intake", "water", "biocompass-watertracker-service")
}

test_raw_permission_also_grants_aggregates if {
    allow with input as record_input("aggregate", ["hydration:intake:read"], "nutrition_intake", "water", "biocompass-watertracker-service")
}

test_blind_delete_is_bound_to_its_source if {
    allow with input as record_input("delete", ["hydration:intake:delete"], "", "", "biocompass-watertracker-service")
    not allow with input as record_input("delete", ["hydration:intake:delete"], "", "", "another-source")
}

test_source_record_scope_never_changes_user_ownership if {
    request := record_input("create", ["hydration:intake:create"], "nutrition_intake", "water", "biocompass-watertracker-service")
    changed := object.union(request, {"resource": object.union(request.resource, {"target_user_id": "another-user"})})
    not allow with input as changed
}

test_summary_only_permission_does_not_grant_aggregates if {
    not allow with input as record_input("aggregate", ["hydration:summary:read"], "nutrition_intake", "water", "biocompass-watertracker-service")
}

test_aggregate_and_read_share_resource_restrictions if {
    every action in ["read", "aggregate"] {
        request := record_input(action, ["hydration:intake:read"], "nutrition_intake", "water", "biocompass-watertracker-service")
        restricted := object.union(request, {"resource": object.union(request.resource, {"privacy_scope": ["restricted"]})})
        not allow with input as restricted
    }
}
