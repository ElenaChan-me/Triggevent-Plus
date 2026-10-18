package gg.xp.xivsupport.triggers.cactbot.arr;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/raid/t5.ts をもとに移植
@CalloutRepo(name = "The Binding Coil Of Bahamut Turn5", duty = KnownDuty.TheBindingCoilOfBahamutTurn5)
public class T5 extends AutoChildEventHandler implements FilteredEventHandler {

	private static final int DEATH_SENTENCE_SOON_DELAY_MS = 30_000;
	private static final int DIVEBOMB_SET_TWO_DELAY_MS = 60_000;

	private XivState state;

	public T5(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheBindingCoilOfBahamutTurn5);
	}

	@NpcCastCallout(0x5B2)
	private final ModifiableCallout<AbilityCastStart> deathSentence = ModifiableCallout.durationBasedCall("Death Sentence", "Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> deathSentenceSoon = new ModifiableCallout<>("Death Sentence Soon", "Death Sentence Soon");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> deathSentenceSoonSq = SqtTemplates.sq(DEATH_SENTENCE_SOON_DELAY_MS + 5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x5B2),
			(e1, s) -> {
				s.waitMs(DEATH_SENTENCE_SOON_DELAY_MS);
				s.updateCall(deathSentenceSoon, e1);
			});

	@NpcCastCallout(0x4DB)
	private final ModifiableCallout<AbilityCastStart> liquidHell = ModifiableCallout.durationBasedCall("Liquid Hell", "Liquid Hell");

	@NpcCastCallout(0x4E1)
	private final ModifiableCallout<AbilityCastStart> twister = ModifiableCallout.durationBasedCall("Twister", "Twister");

	// 複数人に当たると行が分かれるので連続通知を抑える
	@NpcAbilityUsedCallout(value = 0x5B0, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> divebomb = new ModifiableCallout<>("Divebomb", "Divebomb");

	private final ModifiableCallout<AbilityUsedEvent> divebombSoon = new ModifiableCallout<>("Divebombs Soon", "Divebombs Soon");

	// 2セット目の予告は最初のダイブボムから一度だけ出す
	@AutoFeed
	private final SequentialTrigger<BaseEvent> divebombSoonSq = SqtTemplates.multiInvocation(DIVEBOMB_SET_TWO_DELAY_MS + 5_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x5B0),
			(e1, s) -> {
				s.waitMs(DIVEBOMB_SET_TWO_DELAY_MS);
				s.updateCall(divebombSoon, e1);
			});

	private final ModifiableCallout<AbilityUsedEvent> fireballOnYou = new ModifiableCallout<>("Fireball on You", "Fireball on You");
	private final ModifiableCallout<AbilityUsedEvent> fireballOnOther = new ModifiableCallout<>("Fireball on Other", "Fireball on {event.target}");
	private final ModifiableCallout<AbilityUsedEvent> conflagOnYou = new ModifiableCallout<>("Conflagration on You", "Conflag on You");
	private final ModifiableCallout<AbilityUsedEvent> conflagOnOther = new ModifiableCallout<>("Conflagration on Other", "Conflag on {event.target}");
	private final ModifiableCallout<AbilityUsedEvent> knightOnYou = new ModifiableCallout<>("Unwoven Will on You", "Knight on You");
	private final ModifiableCallout<AbilityUsedEvent> knightOnOther = new ModifiableCallout<>("Unwoven Will on Other", "Knight on {event.target}");
	private final ModifiableCallout<AbilityUsedEvent> hatchOnYou = new ModifiableCallout<>("Hatch on You", "Hatch on You");
	private final ModifiableCallout<AbilityUsedEvent> hatchOnOther = new ModifiableCallout<>("Hatch on Other", "Hatch on {event.target}");

	@HandleEvents
	public void targetedAbilities(EventContext context, AbilityUsedEvent event) {
		if (event.getSource().isPc()) {
			return;
		}
		boolean onYou = event.getTarget().isThePlayer();
		switch ((int) event.getAbility().getId()) {
			case 0x5AC -> context.accept((onYou ? fireballOnYou : fireballOnOther).getModified(event));
			case 0x5AB -> context.accept((onYou ? conflagOnYou : conflagOnOther).getModified(event));
			case 0x4E3 -> context.accept((onYou ? knightOnYou : knightOnOther).getModified(event));
			case 0x5AD -> context.accept((onYou ? hatchOnYou : hatchOnOther).getModified(event));
		}
	}

}
