package wordCount;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class WordCountMain {

	public static void main(String[] args) {

		// get url list
		List<String> urlList = getURLList();

		Map<Integer, String> lengthBased = getMaxOccurrencesByLength(urlList);  
                
		// print result
        lengthBased.entrySet().stream()
        .forEach(s -> System.out.println("length " + s.getKey() + ": " + s.getValue()));
	}

	/**
	 * 
	 * @param urlList
	 * @return a map with length as key and max occurred String as value
	 */
	public static Map<Integer, String> getMaxOccurrencesByLength(List<String> urlList) {
		
		List<String> allText = getAllTextFromUrl(urlList);

		final String REGEX_NONE_WORDS = "(\\P{L})";

		// split text by all none words characters.
		// then get all words (more than 1 characters) and group by String to count
		// occurrences
        Map<String, Long> count =
        		allText.parallelStream()
        		.flatMap(string -> Stream.of(string.split(REGEX_NONE_WORDS)))
				.filter(word -> word.length() > 1)
				.map(word -> word.toUpperCase())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        // group by length and set max occurrences for each length.
        // then map to remove the whole Map.Entry and get only sorted map with
        // key = length. value = most common word
        Map<Integer, String> lengthBased = 
        		count.entrySet().parallelStream()
        		.collect(Collectors.groupingBy(
        	            s-> s.getKey().length(),
        	            Collectors.maxBy(Comparator.comparingLong(Map.Entry::getValue))))
        		.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
        		.collect(Collectors.toMap(
	        	            s -> s.getKey(),
	        	            s -> s.getValue().get().getKey(),
	        	            (oldValue, newValue) -> oldValue,
	        	            LinkedHashMap::new));

		return lengthBased;
	}

	/**
	 * go over the url list and for each url use Jsoup to get all the text
	 * using threads
	 * @param urlList 
	 * @return 
	 */
	private static List<String> getAllTextFromUrl(List<String> urlList) {
		ExecutorService executor = Executors.newFixedThreadPool(3);

		// go over all the urls and get it's text using Jsoup
		// split into threads and concat all strings
		List<Future<String>> list = new LinkedList<Future<String>>();
		
		for (String url : urlList) {
			Future<String> future = executor.submit(new Callable<String>() {

				@Override
				public String call() throws Exception {
					try {
						Document doc = Jsoup.connect(url).get();
						return doc.body().text();

					} catch (Exception e) {
						System.out.println("invalid url " + url);
						return "";
					}
				}
			});

			list.add(future);
		}

		List<String> allText = new LinkedList<String>();
		for (Future<String> future : list) {
			try {
				allText.add(future.get());
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}
		
		// shut down the executor service now
		executor.shutdown();
		return allText;
	}

	private static List<String> getURLList() {
		List<String> urlList = new LinkedList<String>();

		Scanner scan = new Scanner(System.in);

		System.out.println("add url list. to finish press -");
		String str = scan.nextLine();

		while (!"-".equals(str)) {
			urlList.add(str);
			str = scan.nextLine();
		}

		scan.close();
		return urlList;
	}
}
