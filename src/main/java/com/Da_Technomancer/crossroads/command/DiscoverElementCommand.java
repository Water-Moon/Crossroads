package com.Da_Technomancer.crossroads.command;

import com.Da_Technomancer.crossroads.API.beams.EnumBeamAlignments;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.text.TranslationTextComponent;

import java.util.Locale;

public class DiscoverElementCommand implements Command<CommandSource>{

	private static final DiscoverElementCommand INSTANCE = new DiscoverElementCommand();
	private static final String RESET = "NONE";//Argument that can be passed to reset player discoveries

	public static ArgumentBuilder<CommandSource, ?> register(CommandDispatcher<CommandSource> dispatcher){
		//Suggested values: lowercase names of all alignments other than no match, and the RESET command
		String[] suggestVals = new String[EnumBeamAlignments.values().length];
		suggestVals[0] = RESET.toLowerCase(Locale.US);
		int ind = 1;
		for(EnumBeamAlignments align : EnumBeamAlignments.values()){
			if(align != EnumBeamAlignments.NO_MATCH){
				suggestVals[ind] = align.name().toLowerCase(Locale.US);
				ind++;
			}
		}
		return Commands.literal("discover").requires(cs -> cs.getEntity() instanceof PlayerEntity && cs.hasPermissionLevel(2))
				.then(Commands.argument("alignment", StringArgumentType.word())
						.suggests((context, builder) -> ISuggestionProvider.suggest(suggestVals, builder)))
				.executes(INSTANCE);

	}

	@Override
	public int run(CommandContext<CommandSource> context) throws CommandSyntaxException{
		String arg = context.getArgument("alignment", String.class);
		arg = arg.toUpperCase(Locale.US);
		PlayerEntity player = context.getSource().asPlayer();
		if(arg.equals(RESET)){
			for(EnumBeamAlignments align : EnumBeamAlignments.values()){
				align.discover(player, false);
			}
		}else{
			try{
				EnumBeamAlignments align = EnumBeamAlignments.valueOf(arg);
				align.discover(player, true);
			}catch(IllegalArgumentException e){
				TranslationTextComponent message = new TranslationTextComponent("crossroads.command.discover.no_elem");
				throw new CommandSyntaxException(new SimpleCommandExceptionType(message), message);
			}
		}
		return 0;
	}
}
