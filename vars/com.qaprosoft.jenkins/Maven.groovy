def call(Map body) {
    node(body.node) {
        for (method in body.methods) {
            method()
        }
    }
}