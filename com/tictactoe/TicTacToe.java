package com.tictactoe;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.json.JSONException;
import org.json.JSONObject;

import com.pubnub.api.Callback;
import com.pubnub.api.Pubnub;
import com.pubnub.api.PubnubException;

/** 
 * Simple Tic Tac Toe game made using the PubNub API with the Java SDK.
 * @author Sean Mahbod
 */
public class TicTacToe {
	
	public static final String LOBBY_CHANNEL = "lobby";
	public static final String WAITING_POOL_CHANNEL = "waiting_pool";
	public static final String PLAYING_CHANNEL = "playing";
	private static final int PLAY_REQUEST = 1;
	private static final int PLAY_REQUEST_ACCEPTED = 2;
	
	public static String opponentUUID;
	public static String opponentNickname = "Anonymous";
	public static String nickname = "Anonymous";
	public static boolean isMyTurn;
	public static boolean isPlaying;
	public static String ourSymbol;
	
	public static Pubnub pubnub;
	public static List<Cell> board;

	public static void main(String[] args) {
		pubnub = new Pubnub("pub-c-ff4631b6-40c7-4294-97da-119d3e18601e", "sub-c-a7fbb89a-09f4-11e6-8c3e-0619f8945a4f");
		
		try {
			pubnub.subscribe(LOBBY_CHANNEL, new Callback() {});
		}
		catch (PubnubException e) {
			System.out.println(e.toString());
		}
		
		System.out.println("Welcome to my Tic Tac Toe! You are in the lobby. Here are the commands you can use:");
		displayHelp();
		Scanner scanner = new Scanner(System.in);
		while (true) {
			if (isPlaying) {
				if (isMyTurn) {
					System.out.print("Enter coordinates in the form \"x y\": ");
				}
			}
			else {
				System.out.print("Enter a command: ");				
			}
			
			//read a line from the console
			String input = scanner.nextLine();
			
			if (input.length() == 0)
				continue;
			
			//get the command
			char command = input.charAt(0);
			
			//split the input and parse it for possible arguments
			String[] splitInput = input.split(" ");
			String[] arguments = null;
			if (splitInput.length > 0) {
				arguments = new String[splitInput.length - 1];
				for (int i = 1; i < splitInput.length; i++) {
					arguments[i - 1] = splitInput[i];
				}
			}
			
			if (isPlaying) {
				int x = -1, y = -1;
				boolean numberError = false;
				if (splitInput.length < 2)
					numberError = true;
				else {
					try {
						x = Integer.parseInt(splitInput[0]);
						y = Integer.parseInt(splitInput[1]);
					}
					catch (NumberFormatException e) {
						numberError = true;
					}					
				}
				
				if (!numberError && !isValidCommand(command)) {
					if (!isMyTurn) {
						System.out.println("It's not your turn yet!");
					}
					else {
						if (!isValidCoordinates(x, y)) {
							System.out.println("Coordinates must be in the range [0, 2]");					
						}
						else {
							if (setCell(x, y, ourSymbol)) {
								isMyTurn = false;
								displayBoard();
								sendPlayMove(x, y);
								if (isVictory(ourSymbol)) {
									pubnub.unsubscribe(PLAYING_CHANNEL);
									try {
										pubnub.subscribe(LOBBY_CHANNEL, new Callback() {});
									} catch (PubnubException e) {
										e.printStackTrace();
									}
									System.out.println("You won! You're back in the lobby now. Enter \"p\" to play again or enter \"h\" for a list of commands.");
									isPlaying = false;
								}
								else if (isTie()) {
									pubnub.unsubscribe(PLAYING_CHANNEL);
									try {
										pubnub.subscribe(LOBBY_CHANNEL, new Callback() {});
									} catch (PubnubException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
									System.out.println("Tie game! You're back in the lobby now. Enter \"p\" to play again or enter \"h\" for a list of commands.");
									isPlaying = false;
								}
								else {
									System.out.println("Waiting for your opponent's move...");	
								}
							}
							else {
								System.out.println("That cell is already occupied.");
							}
						}
					}
				}				
			}
			
			//decide what to do based on the input
			boolean quit = false;
			switch (command) {
			case 'n':
				if (!isPlaying) {
					nickname = arguments[0];					
					System.out.println("Great! Your nickname is now " + nickname + ". You can change it at any time while in the lobby.");
				}
				else {
					System.out.println("You can only change your name in the lobby.");
				}
				break;
			case 'p':
				if (isPlaying) {
					System.out.println("You're already playing!");
					break;
				}
				
				pubnub.unsubscribe(LOBBY_CHANNEL);
				try {
					pubnub.subscribe(WAITING_POOL_CHANNEL, new Callback() {
						@Override
						public void connectCallback(String channel, Object message) {
							System.out.println("Waiting for an opponent...");
							JSONObject obj = new JSONObject();
							try {
								obj.put("uuid_from", pubnub.getUUID());
								obj.put("opponent_nickname", nickname);
								obj.put("request_code", PLAY_REQUEST);
							} catch (JSONException e) {
								e.printStackTrace();
							}
							
							pubnub.publish(WAITING_POOL_CHANNEL, obj, new Callback() {});
						}
						
						@Override
						public void successCallback(String channel, Object message) {
							if (message instanceof JSONObject) {
								JSONObject obj = (JSONObject) message;
								String uuid_from = "";
								int request_code = 0;
								try {
									uuid_from = obj.getString("uuid_from");
									request_code = obj.getInt("request_code");
								}
								catch (JSONException e) {
									e.printStackTrace();
								}
								
								//check and make sure we're not responding to a play request from ourselves
								if (!uuid_from.equals(pubnub.getUUID())) {
									if (request_code == PLAY_REQUEST) {
										//found a partner! Set the both of us up to play
										System.out.println("Found an opponent!");
										opponentUUID = uuid_from;
										final JSONObject response = new JSONObject();
										try {
											response.put("uuid_from", pubnub.getUUID());
											response.put("uuid_to", uuid_from);
											response.put("request_code", PLAY_REQUEST_ACCEPTED);
											isMyTurn = Math.random() < 0.5; //insert random function here
											opponentNickname = obj.getString("opponent_nickname");
											response.put("sender_goes_first", isMyTurn);
											response.put("sender_nickname", nickname);
										}
										catch (JSONException e) {
											e.printStackTrace();
										}
										
										
										//leave the waiting pool channel
										pubnub.unsubscribe(WAITING_POOL_CHANNEL);
										try {
											//join the channel where play messages will be sent
											pubnub.subscribe(PLAYING_CHANNEL, new PlayingCallback(isMyTurn, response));
										} catch (PubnubException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										}	
									}
									else if (request_code == PLAY_REQUEST_ACCEPTED) {
										System.out.println("Found an opponent!");
										String uuid_to = "";
										try {
											uuid_to = obj.getString("uuid_to");
											opponentNickname = obj.getString("sender_nickname");
										}
										catch (JSONException e) {
											e.printStackTrace();
										}
										
										//check to see whether this request acceptance is meant for us
										if (uuid_to.equals(pubnub.getUUID())) {
											opponentUUID = uuid_from;
											try {
												isMyTurn = !obj.getBoolean("sender_goes_first");
											}
											catch (JSONException e) {
												e.printStackTrace();
											}
											
											pubnub.unsubscribe(WAITING_POOL_CHANNEL);
											try {
												pubnub.subscribe(PLAYING_CHANNEL, new PlayingCallback(isMyTurn, null));
											} catch (PubnubException e) {
												// TODO Auto-generated catch block
												e.printStackTrace();
											}
										}
									}	
								}
							}
						}
					});
				} catch (PubnubException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				break;
			case 'q':
				//quit the game
				quit = true;
				break;
			case 'h':
				//display help usage
				displayHelp();
				break;
			case 'a':
				if (isPlaying) {
					abandon();
				}
				else {
					System.out.println("You're not currently in a game.");
				}
				break;
			}
			
			if (quit)
				break;
		}
		
		System.out.println("Thanks for playing!");
		scanner.close();
		pubnub.shutdown();
	}
	
	private static void displayHelp() {
		System.out.println("n <name>  Register a nickname for yourself (optional)");
		System.out.println("p         Play a game");
		System.out.println("q         Exit the application");
		System.out.println("a         Abandon a game and go back to the lobby");
		System.out.println("h         Display this help");
		System.out.println("<int> <int>   Space-separated coordinates to place your symbol during the game");
		System.out.println();
	}
	
	public static void displayBoard() {
		int iterations = 0;
		for (int i = 0; i < board.size(); i++) {
			iterations++;
			System.out.print(board.get(i).value);
			if (i % 3 < 2) {
				System.out.print("|");
			}
			if (iterations == 3) {
				iterations = 0;
				System.out.println();
			}
		}
		System.out.println();
	}
	
	public static void initializeBoard() {
		board = new ArrayList<Cell>();
		for (int y = 2; y >= 0; y--) {
			for (int x = 0; x < 3; x++) {
				String value = y > 0 ? "_" : " ";
				Cell c = new Cell(x, y, value);
				board.add(c);
			}
		}
	}
	
	public static boolean setCell(int x, int y, String value) {
		for (int i = 0; i < board.size(); i++) {
			Cell c = board.get(i);
			if (c.x == x && c.y == y) {
				if (c.value.equals("X") || c.value.equals("O")) {
					return false;
				}
				else {
					c.value = value;
					return true;
				}
			}
		}
		
		return false;
	}
	
	public static String getCell(int x, int y) {
		for (int i = 0; i < board.size(); i++) {
			Cell c = board.get(i);
			if (c.x == x && c.y == y) {
				return c.value;
			}
		}
		
		return "invalid cell";
	}
	
	public static boolean checkCell(int x, int y, String value) {
		return getCell(x, y).equals(value);
	}
	
	private static boolean isValidCommand(char c) {
		return c == 'n' || c == 'p' || c == 'q' || c == 'h';
	}
	
	private static boolean isValidCoordinates(int x, int y) {
		return x >= 0 && x <= 2 && y >= 0 && y <= 2;
	}
	
	public static void sendPlayMove(int x, int y) {
		JSONObject obj = new JSONObject();
		try {
			obj.put("uuid_from", pubnub.getUUID());
			obj.put("nickname", nickname);
			obj.put("x", x);
			obj.put("y", y);
			obj.put("symbol", ourSymbol);
		}
		catch (JSONException e) {
			e.printStackTrace();
		}
		
		//send the move we just made to our opponent
		pubnub.publish(PLAYING_CHANNEL, obj, new Callback() {});
	}
	
	public static boolean isVictory(String symbol) {
		//check first column
		if (checkCell(0, 0, symbol) && checkCell(0, 1, symbol) && checkCell(0, 2, symbol)) {
			return true;
		}
		
		//check second column
		if (checkCell(1, 0, symbol) && checkCell(1, 1, symbol) && checkCell(1, 2, symbol)) {
			return true;
		}
		
		//check third column
		if (checkCell(2, 0, symbol) && checkCell(2, 1, symbol) && checkCell(2, 2, symbol)) {
			return true;
		}
		
		//check first row
		if (checkCell(0, 0, symbol) && checkCell(1, 0, symbol) && checkCell(2, 0, symbol)) {
			return true;
		}
		
		//check second row
		if (checkCell(0, 1, symbol) && checkCell(1, 1, symbol) && checkCell(2, 1, symbol)) {
			return true;
		}
		
		//check third row
		if (checkCell(0, 2, symbol) && checkCell(1, 2, symbol) && checkCell(2, 2, symbol)) {
			return true;
		}
		
		//check first diagonal
		if (checkCell(0, 0, symbol) && checkCell(1, 1, symbol) && checkCell(2, 2, symbol)) {
			return true;
		}
		
		//check second diagonal
		if (checkCell(0, 2, symbol) && checkCell(1, 1, symbol) && checkCell(2, 0, symbol)) {
			return true;
		}
		
		return false;
	}
	
	public static boolean isTie() {
		for (Cell c : board) {
			if (c.value.equals(" ") || c.value.equals("_"))
				return false;
		}
		
		return true;
	}
	
	private static void abandon() {
		pubnub.unsubscribe(PLAYING_CHANNEL);
		try {
			pubnub.subscribe(LOBBY_CHANNEL, new Callback() {});
		} catch (PubnubException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		JSONObject obj = new JSONObject();
		try {			
			obj.put("uuid_from", pubnub.getUUID());
			obj.put("abandon", true);
		}
		catch (JSONException e) {
			e.printStackTrace();
		}
		
		//let our opponent know that we just abandoned the game
		pubnub.publish(PLAYING_CHANNEL, obj, new Callback() {});
		
		System.out.println("You have abandoned the game. You're back in the lobby now.");
		isPlaying = false;
	}
}
