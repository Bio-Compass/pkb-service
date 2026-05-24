package biocompass.pkb.authz

import rego.v1

default allow := false

allowed_purposes := {"care", "self", "operations", "development"}
allowed_owner_actions := {"read", "write", "search"}

allow if {
	input.actor.roles[_] == "pkb_admin"
}

allow if {
	input.actor.user_id == input.resource.owner_user_id
	input.action in allowed_owner_actions
	input.purpose in allowed_purposes
	input.resource.privacy_scope != "restricted"
}

redactions contains "payload" if {
	not allow
}
