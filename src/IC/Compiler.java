package IC;

import java.io.*;
import IC.Parser.*;
import IC.AST.*;
import IC.SymbolTable.GlobalSymbolTable;
import IC.TypeTable.TypeTable;
import IC.Visitors.*;

/*
 * The compiler class
 */
public class Compiler {
	private static boolean printast = false;
	private static boolean dumpsymtab = false;
	private static String libic_path;
	private static boolean libic_flag = false;
	
	/** 
	 * Reads an IC-program, parses (builds an AST) and checks for lexical, syntactic and semantic errors
	 * optional library-file add, pretty-printing of the program's AST and printing of the Symbol and Type tables
	 * @param args[0]: contains the input ic program file path
	 * @param optional: -L<library_path> where library_path is the library-file path 
	 * @param optional: -print-ast to pretty-print the ast (with library class in it, if given)
	 * @param optional: -dump-symtab to print the symbol and type tables 
	 */
	public static void main(String[] args) {
		
		////////////////////////////
		// check input parameters //
		////////////////////////////
		
		// check that received at least one parameter (input ic program path)
		if (args.length == 0) {
			System.out.println("Error: Missing input file argument!");
			printUsage();
			System.exit(-1);
		}
		
		// check for options
		for (int i = 1; i < args.length; i++){
			String s = args[i];
			if (s.equals("-print-ast")){ // -print-ast flag is on
				if (printast){ // already given "-print-ast"
					System.out.println("Error: Wrong usage, -print-ast flag is given more than once");
					printUsage();
					System.exit(-1);
				} else printast = true;
			}
			else if (s.equals("-dump-symtab")) // -dump-symtab flag is on
				if (dumpsymtab){ // already given "-dump-symtab"
					System.out.println("Error: Wrong usage, -dump-symtab flag is given more than once");
					printUsage();
					System.exit(-1);
				} else dumpsymtab = true;
			else if (s.startsWith("-L")){ // library class path is given
				if (libic_flag){ // already given library path
					System.out.println("Error: Wrong usage, library path is given more than once");
					printUsage();
					System.exit(-1);
				} else {
					libic_flag = true;
					libic_path = s.substring(2);
				}
			} else {
				System.out.println("Error: Wrong usage");
				printUsage();
				System.exit(-1);
			}
		}
		
		/////////////////////////////////////////
		// lexical and syntactic parsing phase //
		/////////////////////////////////////////
		
		// Parse the input library file, if given
		// the following are initialized only to prevent error message,
		// real values are given in the try-catch block ahead
		ICClass libraryRoot = new ICClass(0,null,null,null);
		java_cup.runtime.Symbol parseLibrarySymbol = new java_cup.runtime.Symbol(0);
		if (libic_flag) {
			try{
				FileReader libFile = new FileReader(libic_path); // may throw i/o exception
				Lexer libraryLexer = new Lexer(libFile);
				LibraryParser libraryParser = new LibraryParser(libraryLexer);
				parseLibrarySymbol = libraryParser.parse(); // may throw LexicalError or SyntaxError
			} catch (Exception e) {
				System.err.print(e);
				System.exit(-1);
			}
			System.out.println("Parsed " + libic_path + " successfully!");
			libraryRoot = (ICClass) parseLibrarySymbol.value;
		}
		
		// Parse the input ic program file
		// the following is initialized only to prevent error message,
		// real value is given in the try-catch block ahead
		java_cup.runtime.Symbol parseSymbol = new java_cup.runtime.Symbol(1);
		try{
			FileReader txtFile = new FileReader(args[0]); // may throw i/o exception
			Lexer lexer = new Lexer(txtFile);
			Parser parser = new Parser(lexer);
			parseSymbol = parser.parse(); // may throw LexicalError or SyntaxError
		} catch (Exception e){
			System.err.println(e);
			System.exit(-1);
		}
		System.out.println("Parsed " + args[0] + " successfully!");
		Program root = (Program) parseSymbol.value;
		
		// insert library class as another class in the input ic program, if exists
		if (libic_flag) root.addClass(libraryRoot);
		// pretty-print the full AST to System.out
		if (printast){
			PrettyPrinter printer = new PrettyPrinter(args[0]);
			System.out.println(root.accept(printer));
		}
		
		////////////////////////////
		// semantic parsing phase //
		////////////////////////////
		
		// build symbol tables and type table
		// semantic checks for illegal definitions and existance and uniqueness of "main" method
		SymbolTableBuilder symbolTableBuilder = new SymbolTableBuilder(args[0]);
		Object globalSymTab = root.accept(symbolTableBuilder);
		if (globalSymTab == null) System.exit(-1); // in case of an error while building symbol table
		
		if (dumpsymtab){
			System.out.println("\n"+globalSymTab);
			System.out.println(TypeTable.staticToString());
		}
		
		// other semantic checks: variables usage correctness, type checks, scoping rules
		DefTypeCheckingVisitor defTypeCheckingVisitor = new DefTypeCheckingVisitor((GlobalSymbolTable)globalSymTab);
		Object semanticChecks = root.accept(defTypeCheckingVisitor);
		if (semanticChecks == null) {
			System.out.println("Encountered an error while type-checking");
			System.exit(-1); // in case of a semantic error
		} else {
			System.out.println("Passed type-checking");
		}
	}
	
	/**
	 * Prints usage information about this application to System.out
	 */
	public static void printUsage() {
		System.out.println("Usage: IC.Compiler <file.ic> [-L<library_path>] [-print-ast] [-dump-symtab]");
	}
}