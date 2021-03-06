package xyz.arwhite.net.grpcit;

import io.grpc.stub.StreamObserver;
import xyz.arwhite.net.HelloRequest;
import xyz.arwhite.net.HelloResponse;
import xyz.arwhite.net.HelloServiceGrpc.HelloServiceImplBase;

public class HelloServiceImpl extends HelloServiceImplBase {

	 @Override
	    public void hello(
	      HelloRequest request, StreamObserver<HelloResponse> responseObserver) {

	        String greeting = new StringBuilder()
	          .append("Hello, ")
	          .append(request.getFirstName())
	          .append(" ")
	          .append(request.getLastName())
	          .toString();

	        HelloResponse response = HelloResponse.newBuilder()
	          .setGreeting(greeting)
	          .build();

	        responseObserver.onNext(response);
	        responseObserver.onCompleted();
	    }
}
