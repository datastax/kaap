# Scaling the Pulsar Broker with a Kafka Client Workload

This folder contains a sample configuration and demo about how to run a Apache Kafka® workload
on an Apache Pulsar® cluster with the Broker Auto Scaling feature.

Support for the Kafka wire protocol is provided by [Starlight for Kafka](https://github.com/datastax/starlight-for-kafka).

The client work load is generated using the basic Kafka Performance tools.


## Usage

### Deploy the Pulsar cluster

Install the operator and the cluster.

```
helm install pos helm/k8saap-stack --values helm/examples/kafka/values.yaml
```


Deploy the producers
```
kubectl apply -f helm/examples/kafka/kafka-producer-perf.yaml
```

See the logs of the producers
```
kubectl logs -f deploy/kafka-client-producer
```

Deploy the consumers
```
kubectl apply -f helm/examples/kafka/kafka-consumer-perf.yaml
```

See the logs of the consumers
```
kubectl logs -f deploy/kafka-client-consumer
```


### Scaling the client workloads

Open a terminal and see the logs of the Operator
```
kubectl logs -f deploy/k8saap
```


You can use kubectl in order to scale in/out the client applications.
```
kubectl scale deploy/kafka-client-consumer  --replicas 10
```

As the load increases you will see the Operator scales out the Broker STS.