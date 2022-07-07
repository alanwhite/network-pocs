package xyz.arwhite.net.proxit;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Headers {
	
	public static Map<String,List<String>> parse(List<String> headerStrings) {
		
		return headerStrings.stream()
				.collect(Collectors.groupingBy(s -> s.split(":",2)[0].strip(), 
						Collectors.mapping(
								s -> s.split(":",2)[1].strip(),
								Collectors.toList())));
		
	}
	
	public static void prettyPrint(Map<String,List<String>> headers) {
		
		System.out.println("Headers");
		headers.keySet().forEach(key -> {
			System.out.println("\t"+key+":");
			headers.get(key).forEach(header -> {
				System.out.println("\t\t"+header);
			});
		});
		
		
	}

}
