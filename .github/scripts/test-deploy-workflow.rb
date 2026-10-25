require 'yaml'
root = File.expand_path('..', __dir__)
ci = YAML.load_file("#{root}/workflows/ci.yml")
deploy = YAML.load_file("#{root}/workflows/deploy-environment.yml")
def check(condition, message)
  abort(message) unless condition
end
jobs = ci.fetch('jobs')
check(jobs.fetch('deploy-testing').fetch('needs') == 'publish-image', 'Testing must use the CI build')
check(jobs.fetch('e2e-testing').fetch('needs') == 'deploy-testing', 'E2E must run after testing deploy succeeds')
check(jobs.fetch('deploy-prod').fetch('needs').sort == %w[e2e-testing publish-image], 'Prod must require E2E success')
%w[deploy-testing deploy-prod].each do |name|
  job = jobs.fetch(name)
  check(!job.key?('if'), 'Production must retain success-only dependency behavior') if name == 'deploy-prod'
  check(job.fetch('with').fetch('image-ref') == '${{ needs.publish-image.outputs.image-ref }}', 'Both environments must use the same digest')
  check(job.fetch('with').fetch('helm-revision') == '${{ needs.publish-image.outputs.helm-revision }}', 'Both environments must use the same Helm commit')
end
check(jobs.fetch('deploy-testing')['if'] == "github.event_name == 'push' && github.ref == 'refs/heads/main'", 'Manual candidate builds must not deploy')
check(jobs.fetch('publish-image')['if'].include?("github.event_name == 'workflow_dispatch'"), 'Manual builds must publish tested candidates')
check(jobs.fetch('deploy-testing')['with']['environment'] == 'testing', 'Wrong test namespace')
check(jobs.fetch('deploy-prod')['with']['environment'] == 'prod', 'Wrong prod namespace')
check(jobs.fetch('e2e-testing').fetch('environment') == 'testing', 'E2E must use testing credentials')
check(ci.fetch('concurrency')['cancel-in-progress'] != true, 'Do not cancel in-flight main promotions')
check(deploy.fetch('jobs').fetch('approve').fetch('if').include?("inputs.environment == 'prod' ||"), 'Prod must always require manual approval')
apply = deploy.fetch('jobs').fetch('deploy')
check(apply.fetch('needs') == %w[plan approve], 'Deploy must depend on review and approval')
check(apply.fetch('if').include?("needs.plan.result == 'success'"), 'Failed planning must block deploy')
check(apply.fetch('if').include?("inputs.environment == 'testing'"), 'Only testing may bypass approval for image-only changes')
text = File.read("#{root}/workflows/deploy-environment.yml")
check(text.include?('--suppress-secrets'), 'Plan must suppress credential values')
check(text.include?('sh helm-source/scripts/deploy.sh'), 'Use the Helm-owned deployment mechanism')
check(!text.include?('secrets.stringData'), 'Never override existing namespace secrets')
check(text.include?('version: v4.3.1'), 'Shared rollback-on-failure deployment requires Helm 4')
puts 'CI testing/E2E/production promotion checks passed.'
