package org.eclipse.help.internal.search;
/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */
import java.io.*;
import java.util.*;

import org.apache.lucene.analysis.*;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
/**
 * Build query acceptable by the search engine.
 */
public class QueryBuilder {
	/**
	 * User typed expression
	 */
	private String searchWord;
	private Analyzer analyzer;
	/**
	 *  List of QueryWordsToken
	 */
	private List userTokens;
	/**
	 * List of QueryWordsToken
	 */
	private List analyzedTokens;
	/**
	 * ProcessedQuery constructor.
	 * @param userQuery user search query string
	 */
	public QueryBuilder(String searchWord, Analyzer analyzer) {
		this.searchWord = searchWord;
		this.analyzer = analyzer;
		// split search query into tokens
		userTokens = tokenizeUserQuery();
		analyzedTokens = analyzeTokens(userTokens);
	}
	/**
	 * Splits user query into tokens
	 * @return java.util.Vector
	 */
	private List tokenizeUserQuery() {
		List tokenList = new ArrayList();
		//Divide along quotation marks
		StringTokenizer qTokenizer = new StringTokenizer(searchWord.trim(), "\"", true);
		boolean withinQuotation = false;
		String quotedString = "";
		while (qTokenizer.hasMoreTokens()) {
			String curToken = qTokenizer.nextToken();
			if (curToken.equals("\"")) {
				if (withinQuotation) {
					tokenList.add(QueryWordsToken.phrase(quotedString));
				} else {
					quotedString = "";
				}
				withinQuotation = !withinQuotation;
				continue;
			} else if (withinQuotation) {
				quotedString = curToken;
				continue;
			} else {
				//divide unquoted strings along white space
				StringTokenizer parser = new StringTokenizer(curToken.trim());
				while (parser.hasMoreTokens()) {
					String token = parser.nextToken();
					if (token.equalsIgnoreCase(QueryWordsToken.AND().value))
						tokenList.add(QueryWordsToken.AND());
					else if (token.equalsIgnoreCase(QueryWordsToken.OR().value))
						tokenList.add(QueryWordsToken.OR());
					else if (token.equalsIgnoreCase(QueryWordsToken.NOT().value))
						tokenList.add(QueryWordsToken.NOT());
					else
						tokenList.add(QueryWordsToken.word(token));
				}
			}
		}
		return tokenList;
	}
	private List analyzeTokens(List tokens) {
		List newTokens = new ArrayList();
		for (int i = 0; i < tokens.size(); i++) {
			QueryWordsToken token = (QueryWordsToken) tokens.get(i);
			if (token.type == QueryWordsToken.WORD) {
				if (token.value.indexOf('?') >= 0 || token.value.indexOf('*') >= 0) {
					newTokens.add(QueryWordsToken.word(token.value));
				} else {
					List wordList = analyzeText(analyzer, token.value);
					for (Iterator it = wordList.iterator(); it.hasNext();) {
						String word = (String) it.next();
						newTokens.add(QueryWordsToken.word(word));
					}
				}
			} else if (// forget ANDs
			/*token.type == SearchQueryToken.AND
				||*/
				token.type == QueryWordsToken.OR || token.type == QueryWordsToken.NOT)
				newTokens.add(token);
			else if (token.type == QueryWordsToken.PHRASE) {
				QueryWordsPhrase phrase = QueryWordsToken.phrase();
				List wordList = analyzeText(analyzer, token.value);
				for (Iterator it = wordList.iterator(); it.hasNext();) {
					String word = (String) it.next();
					phrase.addWord(word);
				}
				// add phrase only if not empty
				if (phrase.getWords().size() > 0)
					newTokens.add(phrase);
			}
		}
		return newTokens;
	}
	/**
	 * @return List of String
	 */
	private List analyzeText(Analyzer analyzer, String text) {
		List words = new ArrayList(1);
		Reader reader = new StringReader(text);
		TokenStream tStream = analyzer.tokenStream("contents", reader);
		Token tok;
		try {
			while (null != (tok = tStream.next())) {
				words.add(tok.termText());
			}
			reader.close();
		} catch (IOException ioe) {
		}
		return words;
	}
	/**
	 * Obtains Lucene Query from tokens
	 * @return Query or null if no query could be created
	 */
	private Query createLuceneQuery(
		List searchTokens,
		String[] fieldNames,
		float[] boosts) {
		// Get queries for parts separated by OR
		List requiredQueries = getRequiredQueries(searchTokens, fieldNames, boosts);
		if (requiredQueries.size() == 0)
			return null;
		else if (requiredQueries.size() <= 1)
			return (Query) requiredQueries.get(0);
		else /*if (requiredQueries.size() > 1) */
			// OR queries
			return (orQueries(requiredQueries));
	}
	/**
	 * Obtains Lucene queries for token sequences separated at OR.
	 * @return List of Query (could be empty)
	 */
	private List getRequiredQueries(
		List tokens,
		String[] fieldNames,
		float[] boosts) {
		List oredQueries = new ArrayList();
		ArrayList requiredQueryTokens = new ArrayList();
		for (int i = 0; i < tokens.size(); i++) {
			QueryWordsToken token = (QueryWordsToken) tokens.get(i);
			if (token.type != QueryWordsToken.OR) {
				requiredQueryTokens.add(token);
			} else {
				Query reqQuery = getRequiredQuery(requiredQueryTokens, fieldNames, boosts);
				if (reqQuery != null)
					oredQueries.add(reqQuery);
				requiredQueryTokens = new ArrayList();
			}
		}
		Query reqQuery = getRequiredQuery(requiredQueryTokens, fieldNames, boosts);
		if (reqQuery != null)
			oredQueries.add(reqQuery);
		return oredQueries;
	}
	private Query orQueries(Collection queries) {
		BooleanQuery bq = new BooleanQuery();
		for (Iterator it = queries.iterator(); it.hasNext();) {
			Query q = (Query) it.next();
			bq.add(q, false, false);
		}
		return bq;
	}
	/**
	 * Obtains Lucene Query for tokens containing only AND and NOT operators.
	 * @return BooleanQuery or null if no query could be created from the tokens
	 */
	private Query getRequiredQuery(
		List requiredTokens,
		String[] fieldNames,
		float[] boosts) {
		BooleanQuery retQuery = new BooleanQuery();
		boolean requiredTermExist = false;
		// Parse tokens left to right
		QueryWordsToken operator = null;
		for (int i = 0; i < requiredTokens.size(); i++) {
			QueryWordsToken token = (QueryWordsToken) requiredTokens.get(i);
			if (token.type == QueryWordsToken.AND || token.type == QueryWordsToken.NOT) {
				operator = token;
				continue;
			}
			// Creates queries for all fields
			Query qs[] = new Query[fieldNames.length];
			for (int f = 0; f < fieldNames.length; f++) {
				qs[f] = token.createLuceneQuery(fieldNames[f], boosts[f]);
			}
			// creates the boolean query of all fields
			Query q = qs[0];
			if (fieldNames.length > 1) {
				BooleanQuery allFieldsQuery = new BooleanQuery();
				for (int f = 0; f < fieldNames.length; f++)
					allFieldsQuery.add(qs[f], false, false);
				q = allFieldsQuery;
			}
			if (operator != null && operator.type == QueryWordsToken.NOT) {
				retQuery.add(q, false, true); // add as prohibited
			} else {
				retQuery.add(q, true, false); // add as required
				requiredTermExist = true;
			}
		}
		if (!requiredTermExist) {
			return null; // cannot search for prohited only 
		}
		return retQuery;
	}
	private Query getLuceneQuery(String[] fieldNames, float[] boosts) {
		Query luceneQuery = createLuceneQuery(analyzedTokens, fieldNames, boosts);
		return luceneQuery;
	}
	/**
	 * @param fieldNames - Collection of field names of type String (e.g. "h1");
	 *  the search will be performed on the given fields
	 * @param fieldSearch - boolean indicating if field only search
	 *  should be performed; if set to false, default field "contents"
	 *  and all other fields will be searched
	 */
	public Query getLuceneQuery(Collection fieldNames, boolean fieldSearchOnly) {
		String[] fields;
		float[] boosts;
		if (fieldSearchOnly) {
			fields = new String[fieldNames.size()];
			boosts = new float[fieldNames.size()];
			Iterator fieldNamesIt = fieldNames.iterator();
			for (int i = 0; i < fieldNames.size(); i++) {
				fields[i] = (String) fieldNamesIt.next();
				boosts[i] = 5.0f;
			}
		} else {
			fields = new String[fieldNames.size() + 1];
			boosts = new float[fieldNames.size() + 1];
			Iterator fieldNamesIt = fieldNames.iterator();
			for (int i = 0; i < fieldNames.size(); i++) {
				fields[i] = (String) fieldNamesIt.next();
				boosts[i] = 5.0f;
			}
			fields[fieldNames.size()] = "contents";
			boosts[fieldNames.size()] = 1.0f;
		}
		Query query = getLuceneQuery(fields, boosts);
		query = improveRankingForUnqotedPhrase(query, fields, boosts);
		return query;
	}
	/**
	 * If user query contained only words (no quotaions nor operators)
	 * extends query with term phrase representing entire user query
	 * i.e for user string a b, the query a AND b will be extended
	 * to "a b" OR a AND b
	 */
	private Query improveRankingForUnqotedPhrase(
		Query query,
		String[] fields,
		float[] boosts) {
		if (query == null)
			return query;
		// check if all tokens are words
		for (int i = 0; i < analyzedTokens.size(); i++)
			if (((QueryWordsToken) analyzedTokens.get(i)).type != QueryWordsToken.WORD)
				return query;
		// Create phrase query for all tokens and OR with original query
		BooleanQuery booleanQuery = new BooleanQuery();
		booleanQuery.add(query, false, false);
		PhraseQuery[] phraseQueries = new PhraseQuery[fields.length];
		for (int f = 0; f < fields.length; f++) {
			phraseQueries[f] = new PhraseQuery();
			for (int i = 0; i < analyzedTokens.size(); i++) {
				Term t = new Term(fields[f], ((QueryWordsToken) analyzedTokens.get(i)).value);
				phraseQueries[f].add(t);
			}
			phraseQueries[f].setBoost(10 * boosts[f]);
			booleanQuery.add(phraseQueries[f], false, false);
		}
		return booleanQuery;
	}
	/**
	 * Obtains analyzed words from query as one string.
	 * Words are separated by space.
	 * The analyzed words are needed for highlighting
	 * word roots.
	 */
	public String getAnalyzedWords() {
		StringBuffer buf = new StringBuffer();
		for (Iterator it = analyzedTokens.iterator(); it.hasNext();) {
			QueryWordsToken token = (QueryWordsToken) it.next();
			if (token instanceof QueryWordsPhrase) {
				List words = ((QueryWordsPhrase) token).getWords();
				for (Iterator it2 = words.iterator(); it2.hasNext();) {
					buf.append(' ');
					buf.append((String) it2.next());
				}
			} else {
				buf.append(' ');
				buf.append(token.value);
			}
		}
		if (buf.length() > 1 && buf.charAt(0) == ' ')
			return buf.substring(1);
		return buf.toString();
	}
}