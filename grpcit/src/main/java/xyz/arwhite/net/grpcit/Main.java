package xyz.arwhite.net.grpcit;

import java.net.ProxySelector;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import xyz.arwhite.net.HelloRequest;
import xyz.arwhite.net.HelloResponse;
import xyz.arwhite.net.HelloServiceGrpc;

public class Main {

	public Main() {
		// TODO Auto-generated constructor stub
	}

	public static void main(String[] args) throws Exception {

		// Server Side
		Server server = ServerBuilder
				.forPort(8080)
				.addService(new HelloServiceImpl()).build();

		server.start();

		// Client Side
		var selector = new ProxitSelector();
		ProxySelector.setDefault(selector);
		
		ManagedChannel channel = ManagedChannelBuilder
				.forAddress("localhost", 8080)
				.usePlaintext()
				.build();

		HelloServiceGrpc.HelloServiceBlockingStub stub = HelloServiceGrpc.newBlockingStub(channel);

		HelloResponse helloResponse = stub.hello(HelloRequest.newBuilder()
				.setFirstName("Alan")
				.setLastName("White")
				.build());

		System.out.println(helloResponse.getGreeting());
		
		channel.shutdown();

		// server.awaitTermination();

	}

}
