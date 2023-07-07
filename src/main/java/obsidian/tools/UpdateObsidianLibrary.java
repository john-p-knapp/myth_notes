/*******************************************************************************
 * Copyright (c) 2013, 2017 John Knapp
 * All rights reserved. 
 *******************************************************************************/
package obsidian.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
import org.irp.word.MythFilesParser.OtherContext;
import org.irp.word.MythFilesParser.StatementContext;
import org.irp.word.MythFilesParser.TermContext;
import org.irp.word.MythFilesParser.UndefinedContext;

public class UpdateObsidianLibrary {

	private class RewriteVisitor extends MythFilesBaseVisitor<String> {
		private Writer writer;

		private RewriteVisitor(Writer writer) {
			this.writer = writer;
		}

		@Override
		public String visitTerm(TermContext ctx) {
			try {

				String txt = ctx.getText();

				writer.write("[[");
				if (reverseAliasLookup.containsKey(txt.toLowerCase())) {
					String aliasRoot = reverseAliasLookup.get(txt.toLowerCase());
					rootTerms.add(aliasRoot);
					writer.write(aliasRoot);
					writer.write("|");
				} else {
					rootTerms.add(txt.toLowerCase());

				}

				writer.write(txt);

				writer.write("]]");

			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			return super.visitTerm(ctx);
		}

		

		@Override
		public String visitOther(OtherContext ctx) {
			try {
				writer.write(ctx.getText());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			return super.visitOther(ctx);
		}

		@Override
		public String visitUndefined(UndefinedContext ctx) {
			try {
				writer.write(ctx.getText());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			return super.visitUndefined(ctx);
		}

	}

	private class SpellingVisitor extends MythFilesBaseVisitor<String> {

		StringBuffer buffer = new StringBuffer();

		private SpellingVisitor() {

		}

		@Override
		public String visitStatement(StatementContext ctx) {
			super.visitStatement(ctx);
			return buffer.toString();
		}

		@Override
		public String visitTerm(TermContext ctx) {
			buffer.append(ctx.getText());
			return super.visitTerm(ctx);
		}

		@Override
		public String visitOther(OtherContext ctx) {
			buffer.append(ctx.getText());
			return super.visitOther(ctx);
		}

		@Override
		public String visitUndefined(UndefinedContext ctx) {
			String txt = ctx.getText();
			if (misspellings.containsKey(txt.toLowerCase())) {

				txt = misspellings.get(txt.toLowerCase());

				buffer.append(txt);
				return super.visitUndefined(ctx);
			} else {
				buffer.append(ctx.getText());
				return super.visitUndefined(ctx);
			}
		}

	}

	private final String vaultLocation;

	public UpdateObsidianLibrary(String location) {
		vaultLocation = location;
	}

	public static void main(String[] args) {

		UpdateObsidianLibrary foo = new UpdateObsidianLibrary(args[0]);
		try {
			foo.updateFiles();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	
	private Set<String> rootTerms = new HashSet<>();
	private Map<String, String> reverseAliasLookup = new HashMap<>();
	private Map<String, List<String>> aliasListing = new HashMap<>();
	private Map<String, String> misspellings = new HashMap<>();
	private Map<String, List<String>> mispellingListing = new HashMap<>();

	private void importLookup(String lookupFile, Map<String, String> reverseLookup, Map<String, List<String>> listing)
			throws FileNotFoundException, IOException {

		try (BufferedReader br = new BufferedReader(new FileReader(lookupFile))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] values = line.split(",");
				List<String> elements = new ArrayList<>();
				for (int i = 1; i < values.length; i++) {
					reverseLookup.put(values[i].toLowerCase(), values[0]);
					elements.add(values[i]);
				}
				listing.put(values[0].toLowerCase(), elements);
			}
		}

	}

	private void rewriteDir(String inputDir, String outputDir) throws IOException {
		File logDir = new File(inputDir);
		for (final File entry : logDir.listFiles()) {

			rewriteFile(entry.getAbsolutePath(), outputDir + entry.getName());

		}
	}

	private void rewriteFile(String infile, String outfile) throws IOException {

		CharStream instream = CharStreams.fromFileName(infile);

		Lexer lexer = new MythFilesLexer(instream);

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

		SpellingVisitor spelling = new SpellingVisitor();

		String corrected = spelling.visit(stat);

		CharStream correctedStream = CharStreams.fromString(corrected);

		Lexer correctedLexer = new MythFilesLexer(correctedStream);

		CommonTokenStream correctedTokens = new CommonTokenStream(correctedLexer);

		MythFilesParser correctedParser = new MythFilesParser(correctedTokens);

		correctedParser.addErrorListener(new BaseErrorListener() {
			@Override
			public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
					int charPositionInLine, String msg, RecognitionException e) {
				throw new IllegalArgumentException("failed to parse at line " + line + " due to " + msg);
			}
		});

		StatementContext correctedStat = correctedParser.statement();

		File output = new File(outfile);
		output.getParentFile().mkdirs();
		output.createNewFile();
		PrintWriter writer = new PrintWriter(output);
		RewriteVisitor visitor = new RewriteVisitor(writer);
		visitor.visitStatement(correctedStat);
		writer.flush();
		writer.close();

	}

	private final String inputDir = "src/main/resources";

	public void updateFiles() throws IOException {

		importLookup(inputDir + "/alias_list.txt", reverseAliasLookup, aliasListing);
		importLookup(inputDir + "/misspellings.txt", misspellings, mispellingListing);

		rewriteDir(inputDir + "/raw_logs/", vaultLocation + "/book/");
		
		File vault = new File(vaultLocation);
		for (String term : rootTerms) {
			File entry = getExistingFile(term + ".md", vault);
			if (entry == null) {
				
				writeGeneratedNote(term);

			} else {

				String content = new String(Files.readAllBytes(Paths.get(entry.getPath())));
				if (!content.contains(getSpellingEntry(term))) {
					System.out.println(term + " : " + getSpellingEntry(term));
				}
				if (!content.contains(getAliasEntry(term))) {
					System.out.println(term + " : " + getAliasEntry(term));
				}

			}
		}
	}

	private File getExistingFile(String search, File root) {

		if (root.isDirectory()) {
			File[] files = root.listFiles();
			for (File file : files) {
				File f = getExistingFile(search, file);
				if (f != null) {
					return f;
				}
			}
		} else {

			if (root.getName().toLowerCase().equalsIgnoreCase(search.toLowerCase())) {
				return root;
			}

		}
		return null;
	}

	private String getSpellingEntry(String term) {
		String key = term.toLowerCase();

		StringBuffer buffer = new StringBuffer();

		List<String> spellings = mispellingListing.get(key);

		if (spellings != null && spellings.size() > 0) {
			buffer.append("Corrected spellings: (");
			for (int i = 0; i < spellings.size(); i++) {
				buffer.append(spellings.get(i));
				if (i < spellings.size() - 1) {
					buffer.append(", ");
				}
			}
			buffer.append(")\n");

		}

		return buffer.toString();

	}

	private String getAliasEntry(String term) {
		String key = term.toLowerCase();

		StringBuffer buffer = new StringBuffer();
		List<String> aliases = aliasListing.get(key);
		if (aliases != null && aliases.size() > 0) {
			buffer.append("---\naliases: [");
			for (int i = 0; i < aliases.size(); i++) {
				buffer.append(aliases.get(i));
				if (i < aliases.size() - 1) {
					buffer.append(", ");
				}
			}
			buffer.append("]\n---\n");
		}
		return buffer.toString();

	}

	private void writeGeneratedNote(String term) throws FileNotFoundException {
		String key = term.toLowerCase();

		File file = new File(vaultLocation + "/generated/"
				 + key + ".md");
		file.getParentFile().mkdirs();

		PrintWriter genNoteWriter = new PrintWriter(file);

		genNoteWriter.write(getAliasEntry(key));

		genNoteWriter.write(getSpellingEntry(key));

		genNoteWriter.flush();
		genNoteWriter.close();

	}

}
