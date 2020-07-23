def call(version, registry, image, registryCreds) {
	println "dockerBuild -> call"
	push(build(version, registry), registryCreds)
}


def build(version, registry) {
	docker.build(registry + ":${version}", "--build-arg version=${version} .")
}

def push(image, registryCreds) {
	docker.withRegistry('', registryCreds) {
		image.push()
	}
}