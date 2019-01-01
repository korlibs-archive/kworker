# Kworker

## Strategy:

In the entry point you have to define two lambdas. One lambda is executed per work in an isolated worker,
the other lambda is executed for the main thread once.

### JVM:
* Create a new class loader sharing only platform classes
* Execute worker code in a separate Thread in the other class loader, so it is completely isolated from the main thread

### JS:
* Load all the code in a worker (similar to forking). Using `importScripts` to load all the scripts from the main page.
  Potentially using requirejs. Might require a small js file to have the same domain. (See OLD_PROOF_OF_CONCEPT)
* The entrypoint should switch  
  
### Native:
* Use Kotlin-Native Worker API.