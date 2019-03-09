package me.shadorc.shadbot.command.game.dice;

import java.util.List;
import java.util.function.Consumer;

import discord4j.core.spec.EmbedCreateSpec;
import me.shadorc.shadbot.core.command.Context;
import me.shadorc.shadbot.core.game.GameCmd;
import me.shadorc.shadbot.exception.CommandException;
import me.shadorc.shadbot.exception.MissingArgumentException;
import me.shadorc.shadbot.utils.DiscordUtils;
import me.shadorc.shadbot.utils.NumberUtils;
import me.shadorc.shadbot.utils.Utils;
import me.shadorc.shadbot.utils.embed.help.HelpBuilder;
import me.shadorc.shadbot.utils.object.Emoji;
import reactor.core.publisher.Mono;

public class DiceCmd extends GameCmd<DiceManager> {

	protected static final float MULTIPLIER = 4.5f;
	private static final int MAX_BET = 250_000;

	public DiceCmd() {
		super(List.of("dice"));
	}

	@Override
	public Mono<Void> execute(Context context) {
		final List<String> args = context.requireArgs(1, 2);

		// This value indicates if the user is trying to join or create a game
		final boolean isJoining = args.size() == 1;

		final String numStr = args.get(isJoining ? 0 : 1);
		final Integer num = NumberUtils.asIntBetween(numStr, 1, 6);
		if(num == null) {
			return Mono.error(new CommandException(String.format("`%s` is not a valid number, must be between 1 and 6.",
					numStr)));
		}

		DiceManager diceManager = this.getManagers().get(context.getChannelId());

		// The user tries to join a game and no game are currently playing
		if(isJoining && diceManager == null) {
			return Mono.error(new MissingArgumentException());
		}

		final String betStr = isJoining ? Integer.toString(diceManager.getBet()) : args.get(0);
		final Integer bet = Utils.requireBet(context.getMember(), betStr, MAX_BET);

		if(!isJoining) {
			// The user tries to start a game and it has already been started
			if(diceManager != null) {
				return context.getChannel()
						.flatMap(channel -> DiscordUtils.sendMessage(String.format(Emoji.INFO + " (**%s**) A **Dice Game** has already been started. "
								+ "Use `%s%s <num>` to join it.",
								context.getUsername(), context.getPrefix(), this.getName()), channel))
						.then();
			}

			diceManager = new DiceManager(this, context, bet);
		}

		if(diceManager.getPlayerCount() == 6) {
			return context.getChannel()
					.flatMap(channel -> DiscordUtils.sendMessage(String.format(Emoji.GREY_EXCLAMATION + " (**%s**) Sorry, there are already 6 players.",
							context.getUsername()), channel))
					.then();
		}

		if(diceManager.isNumBet(num)) {
			return context.getChannel()
					.flatMap(channel -> DiscordUtils.sendMessage(
							String.format(Emoji.GREY_EXCLAMATION + " (**%s**) This number has already been bet, please try with another one.",
									context.getUsername()), channel))
					.then();
		}

		if(this.getManagers().putIfAbsent(context.getChannelId(), diceManager) == null) {
			diceManager.start();
		}

		if(diceManager.addPlayerIfAbsent(context.getAuthorId(), num)) {
			return diceManager.show();
		} else {
			return context.getChannel()
					.flatMap(channel -> DiscordUtils.sendMessage(String.format(Emoji.INFO + " (**%s**) You're already participating.",
							context.getUsername()), channel))
					.then();
		}
	}

	@Override
	public Consumer<EmbedCreateSpec> getHelp(Context context) {
		return new HelpBuilder(this, context)
				.setDescription("Start a dice game with a common bet.")
				.addArg("bet", false)
				.addArg("num", "number between 1 and 6\nYou can't bet on a number that has already been chosen by another player.", false)
				.setGains("The winner gets the prize pool plus %.1f times his bet", MULTIPLIER)
				.build();
	}
}
