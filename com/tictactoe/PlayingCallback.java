package com.tictactoe;

import org.json.JSONException;
import org.json.JSONObject;

import com.pubnub.api.Callback;
import com.pubnub.api.PubnubException;

public class PlayingCallback extends Callback {

	private boolean weGoFirst;
	private JSONObject connectionResponse;
	
	public PlayingCallback(boolean weGoFirst, JSONObject connectionResponse) {
		this.weGoFirst = weGoFirst;
		this.connectionResponse = connectionResponse;
	}
	
	@Override
	public void connectCallback(String channel, Object message) {
		TicTacToe.initializeBoard();
		
		if (connectionResponse != null)
			TicTacToe.pubnub.publish(TicTacToe.WAITING_POOL_CHANNEL, connectionResponse, new Callback() {});
		
		if (weGoFirst) {	
			//System.out.println("You go first. You are X's. Your opponent is " + Runner.opponentUUID + " (" + Runner.opponentNickname + ").");
			System.out.println("You go first. You are X's. Your opponent is " + TicTacToe.opponentNickname + ".");
			TicTacToe.ourSymbol = "X";
		}
		else {
			//System.out.println("You go second. You are O's. Your opponent is " + Runner.opponentUUID + " (" + Runner.opponentNickname + ").");
			System.out.println("You go second. You are O's. Your opponent is " + TicTacToe.opponentNickname + ".");
			TicTacToe.ourSymbol = "O";
		}
		
		System.out.println("Specify where to go in the format \"x y\", where x and y are 0 through 2, starting from the bottom left cell.");
		TicTacToe.displayBoard();
		TicTacToe.isPlaying = true;
		if (weGoFirst)
			System.out.print("Enter coordinates in the form \"x y\": ");
		else
			System.out.println("Waiting for your opponent's move...");
	}
	
	@Override
	public void successCallback(String channel, Object message) {
		JSONObject obj = (JSONObject) message;
		String opponent = "";
		try {
			opponent = obj.getString("uuid_from");
			//System.out.println("Received play message from " + opponent);
			if (obj.has("abandon")) {
				System.out.println("Your opponent (" + TicTacToe.opponentNickname + ") has abandoned the game. You're back in the lobby now.");
				System.out.print("Enter a command: ");
				TicTacToe.isPlaying = false;
				TicTacToe.pubnub.unsubscribe(TicTacToe.PLAYING_CHANNEL);
				try {
					TicTacToe.pubnub.subscribe(TicTacToe.LOBBY_CHANNEL, new Callback() {});
					return;
				} catch (PubnubException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		catch (JSONException e) {
			e.printStackTrace();
		}
			
		//make sure we're getting messages from the right player, since messages in this channel are broadcast
		if (TicTacToe.opponentUUID.equals(opponent)) {
			int x = -1;
			int y = -1;
			String symbol = "?";
			String nickname = "Anonymous";
			try {
				x = obj.getInt("x");
				y = obj.getInt("y");
				symbol = obj.getString("symbol");
				nickname = obj.getString("nickname");
			}
			catch (JSONException e) {
				e.printStackTrace();
			}
			
			System.out.println("Your opponent (" + nickname + ") has placed an " + symbol + " at " + x + ", " + y + "!");
			TicTacToe.setCell(x, y, symbol);
			TicTacToe.displayBoard();
			if (TicTacToe.isVictory(symbol)) {
				TicTacToe.pubnub.unsubscribe(TicTacToe.PLAYING_CHANNEL);
				try {
					TicTacToe.pubnub.subscribe(TicTacToe.LOBBY_CHANNEL, new Callback() {});
				} catch (PubnubException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				System.out.println("Your oppponent (" + nickname + ") won! You're back in the lobby now. Enter \"p\" to play again or enter \"h\" for a list of commands.");
				System.out.print("Enter a command: ");
				TicTacToe.isPlaying = false;
			}
			else if (TicTacToe.isTie()) {
				TicTacToe.pubnub.unsubscribe(TicTacToe.PLAYING_CHANNEL);
				try {
					TicTacToe.pubnub.subscribe(TicTacToe.LOBBY_CHANNEL, new Callback() {});
				} catch (PubnubException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				System.out.println("Tie game! You're back in the lobby now. Enter \"p\" to play again or enter \"h\" for a list of commands.");
				System.out.print("Enter a command: ");
				TicTacToe.isPlaying = false;
			}
			else {
				TicTacToe.isMyTurn = true;
				System.out.print("Enter coordinates in the form \"x y\": ");
			}
		} 
	}
}
