package obsidian.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

/*******************************************************************************
 * Copyright (c) 2013, 2017 John Knapp
 * All rights reserved. 
 *******************************************************************************/

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.irp.word.MythFilesBaseVisitor;
import org.irp.word.MythFilesLexer;
import org.irp.word.MythFilesParser;
import org.irp.word.MythFilesParser.StatementContext;
import org.irp.word.MythFilesParser.UndefinedContext;

public class MissingWords {

	private static class MissingWordVistor extends MythFilesBaseVisitor<Map<String, Integer>> {
		public MissingWordVistor(CommonTokenStream tokens) {

		}

		public final HashMap<String, Integer> foundWords = new HashMap<>();

		@Override
		public Map<String, Integer> visitUndefined(UndefinedContext ctx) {
			String key = ctx.getText();
			Integer value = foundWords.getOrDefault(key, 0);
			value++;
			foundWords.put(key, value);
			return foundWords;
		}

	}

	public void findwords() throws IOException {

		String logs = "src/main/resources/raw_logs/";

		Map<String, Integer> fileWords = extractWordsFromDir(logs);

		String english = "src/main/resources/words_alpha.txt";
		String ignore = "src/main/resources/ignore_words.txt";

		Set<String> englishWords = new HashSet<>(); 
		englishWords.addAll(extractWords(english).keySet());
		englishWords.addAll(extractWords(ignore).keySet());
		englishWords.addAll(importCSV("src/main/resources/missing.txt"));
		englishWords.addAll(importCSV("src/main/resources/misspellings.txt"));
		englishWords.addAll(importCSV("src/main/resources/alias_list.txt"));
		
		Set<String> knownwords = new HashSet<>();
		for (String w : englishWords) {
			knownwords.add(w.toLowerCase());
		}

		Map<String, Integer> termsHistogram = new HashMap<>();
		for (String w : fileWords.keySet()) {
			if (!knownwords.contains(w.toLowerCase())) {
				termsHistogram.put(w, fileWords.get(w));
			}

		}

		System.out.println(termsHistogram.size());
		System.out.println(termsHistogram.toString());

	}

	private Set<String> importCSV(String file) throws FileNotFoundException, IOException {
		Set<String> output = new HashSet<>();
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] values = line.split(",");
				for (int i = 0; i < values.length; i++) {
					output.add(values[i].toLowerCase());
				}
			}
		}
		return output;
	}

	private Map<String, Integer> extractWordsFromDir(String path) throws IOException {
		File refDir = new File(path);
		Map<String, Integer> output = new HashMap<>();
		Map<String, Integer> extract;
		for (final File entry : refDir.listFiles()) {
			addMaps(output, extractWords(entry.getAbsolutePath()));
		}
		return output;
	}

	private void addMaps(Map<String, Integer> base, Map<String, Integer> addition) {
		for (String key : addition.keySet()) {
			Integer value = base.getOrDefault(key, 0);
			value += addition.getOrDefault(key, 0);
			base.put(key, value);
		}
	}

	private Map<String, Integer> extractWords(String file) throws IOException {

		CharStream stream = CharStreams.fromFileName(file);

		Lexer lexer = new MythFilesLexer(stream);

		CommonTokenStream tokens = new CommonTokenStream(lexer);

		MythFilesParser parser = new MythFilesParser(tokens);

		parser.addErrorListener(new BaseErrorListener() {
			@Override
			public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
					int charPositionInLine, String msg, RecognitionException e) {
				throw new IllegalArgumentException("failed to parse at line " + line + " due to " + msg);
			}
		});

		StatementContext stat = parser.statement();
		MissingWordVistor finder = new MissingWordVistor(tokens);
		finder.visitStatement(stat);

		return finder.foundWords;
	}

	public static void main(String[] args) {
		MissingWords foo = new MissingWords();
		try {
			foo.findwords();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
