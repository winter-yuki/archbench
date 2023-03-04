# archbench

Benchmarking different java server architectures.

Server receives ProtoBuf message with array, sorts it using bubble sort and sends back sorted.
* **Blocking arch**. One thread per client that receives messages, one thread per client that sends messages. Fixed thread pool for computations.
* **Nonblocking arch**. One thread for read selection, one thread for write selection. Fixed thread pool for computations.
* **Asynchronous arch**. Asynchronous input/output. Fixed thread pool for computations.

Metrics:
* **Server computing time** - array sorting time in the fixed thread pool.
* **Server request processing time** - total amount of time that single request were on server.
* **Client single response time** - average time that clients await for response.

## Getting started

### Run tests

```bash
$ ./gradlew :build
```

### Run experiments

```bash
$ ./gradlew :runner:run
```

### Run 

## Results

### Number of elements

![nElementsServerComputingTime](https://user-images.githubusercontent.com/25281147/214635516-a67759dd-be01-4b81-82e1-f2b1dec1fc04.png)

![nElementsServerRequestProcessingTime](https://user-images.githubusercontent.com/25281147/214635535-71ffb737-dcf5-4ee5-b2ce-e42a9453f03e.png)

![nElementsClientSingleResponseTime](https://user-images.githubusercontent.com/25281147/214635502-062a25cd-1b2f-4890-89cd-4dd58d9a9d54.png)

### Number of clients

![nClientsServerComputingTime](https://user-images.githubusercontent.com/25281147/214635475-1f3d1a7a-dd17-49e9-ae6e-d0676bb138e1.png)

![nClientsServerRequestProcessingTime](https://user-images.githubusercontent.com/25281147/214635488-9569163a-7f3f-4150-a42d-3a39366fa917.png)

![nClientsClientSingleResponseTime](https://user-images.githubusercontent.com/25281147/214635436-adc32621-9fce-4572-a2ff-14ec3cd292de.png)

### Delay between requests

![delaysServerComputingTime](https://user-images.githubusercontent.com/25281147/214635242-98faecbe-ca88-4989-8b5b-93c5cb78a43f.png)

![delaysServerRequestProcessingTime](https://user-images.githubusercontent.com/25281147/214635392-4c6b5e0c-fcad-47fc-90c6-677b20c3a7dc.png)

![delaysClientSingleResponseTime](https://user-images.githubusercontent.com/25281147/214635347-3f46c488-01da-44b0-8aaa-af55c854c38e.png)
