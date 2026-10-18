package gg.xp.xivsupport.triggers.cactbot.sb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.Job;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.BattleTalkEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/trial/tsukuyomi-ex.ts をもとに移植
@CalloutRepo(name = "The Minstrels Ballad Tsukuyomis Pain", duty = KnownDuty.TheMinstrelsBalladTsukuyomisPain)
public class TsukuyomiEx extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long MOONLIT = 0x602;
	private static final long MOONSHADOWED = 0x603;
	// 同じ色のデバフが4スタックになったら反対の色へ移動する
	private static final int MOON_MOVE_STACKS = 4;

	private XivState state;

	public TsukuyomiEx(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheMinstrelsBalladTsukuyomisPain);
	}

	private boolean moonIsOut;
	// null は未初期化。最初の1回目で移動指示を出したいので3から数え始める
	private Integer moonlitCount;
	private Integer moonshadowedCount;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		moonIsOut = false;
		moonlitCount = null;
		moonshadowedCount = null;
	}

	@NpcCastCallout(0x2BBA)
	private final ModifiableCallout<AbilityCastStart> reprimand = ModifiableCallout.durationBasedCall("Reprimand", "Raidwide");

	@NpcCastCallout(0x2BBC)
	private final ModifiableCallout<AbilityCastStart> nightfallGun = ModifiableCallout.durationBasedCall("Nightfall (Gun)", "Stack");

	@NpcCastCallout(0x2BBD)
	private final ModifiableCallout<AbilityCastStart> nightfallSpear = ModifiableCallout.durationBasedCall("Nightfall (Spear)", "Role Positions");

	private final ModifiableCallout<AbilityCastStart> tormentOnYou = ModifiableCallout.durationBasedCall("Torment (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> tormentTank = ModifiableCallout.durationBasedCall("Torment (Other Tank)", "Tank Swap");
	private final ModifiableCallout<AbilityCastStart> tormentHealer = ModifiableCallout.durationBasedCall("Torment (Healer)", "Buster on {event.target}");
	private final ModifiableCallout<AbilityCastStart> tormentOthers = ModifiableCallout.durationBasedCall("Torment (Others)", "Get Out of Front");

	@HandleEvents
	public void torment(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x2BBB, 0x2EB2)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(tormentOnYou.getModified(event));
		}
		else if (state.playerJobMatches(Job::isTank)) {
			context.accept(tormentTank.getModified(event));
		}
		else if (state.playerJobMatches(Job::isHealer)) {
			context.accept(tormentHealer.getModified(event));
		}
		else {
			context.accept(tormentOthers.getModified(event));
		}
	}

	@HandleEvents
	public void moonTracker(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x5FF)) {
			moonIsOut = true;
		}
		else if (event.buffIdMatches(0x600)) {
			moonIsOut = false;
		}
	}

	private final ModifiableCallout<AbilityCastStart> darkBladeOut = ModifiableCallout.durationBasedCall("Dark Blade (Full Moon)", "Left and Out");
	private final ModifiableCallout<AbilityCastStart> darkBladeIn = ModifiableCallout.durationBasedCall("Dark Blade (New Moon)", "Left and In");
	private final ModifiableCallout<AbilityCastStart> brightBladeOut = ModifiableCallout.durationBasedCall("Bright Blade (Full Moon)", "Right and Out");
	private final ModifiableCallout<AbilityCastStart> brightBladeIn = ModifiableCallout.durationBasedCall("Bright Blade (New Moon)", "Right and In");

	@HandleEvents
	public void blades(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x2BDA)) {
			context.accept((moonIsOut ? darkBladeOut : darkBladeIn).getModified(event));
		}
		else if (event.abilityIdMatches(0x2BDB)) {
			context.accept((moonIsOut ? brightBladeOut : brightBladeIn).getModified(event));
		}
	}

	@PlayerHeadmarker(0x83)
	private final ModifiableCallout<HeadMarkerEvent> meteor = new ModifiableCallout<>("Meteor Marker", "Meteor on You");

	private final ModifiableCallout<HeadMarkerEvent> lunacy = new ModifiableCallout<>("Lunacy", "Stack");

	@HandleEvents
	public void lunacy(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(lunacy.getModified(event));
		}
	}

	@PlayerHeadmarker(0x17)
	private final ModifiableCallout<HeadMarkerEvent> hagetsu = new ModifiableCallout<>("Hagetsu", "Spread");

	private final ModifiableCallout<BattleTalkEvent> danceOfTheDead = new ModifiableCallout<>("Dance of the Dead", "Raidwide");

	@HandleEvents
	public void danceOfTheDead(EventContext context, BattleTalkEvent event) {
		// 詠唱が無く、残り35%で押し込まれるとこのセリフの後に使ってくる
		if (event.getInstanceContentTextId() == 0x4C41) {
			context.accept(danceOfTheDead.getModified(event));
		}
	}

	private final RepeatSuppressor selenomancySupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void supremeSelenomancy(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x2EB0) && selenomancySupp.check(event)) {
			moonlitCount = null;
			moonshadowedCount = null;
		}
	}

	private final ModifiableCallout<BuffApplied> moveToBlack = new ModifiableCallout<BuffApplied>("Moonlit", "Move to Black").statusIcon(MOONLIT);
	private final ModifiableCallout<BuffApplied> moveToWhite = new ModifiableCallout<BuffApplied>("Moonshadowed", "Move to White").statusIcon(MOONSHADOWED);

	@HandleEvents
	public void moonDebuffs(EventContext context, BuffApplied event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		if (event.buffIdMatches(MOONLIT)) {
			moonlitCount = nextMoonCount(moonlitCount);
			moonshadowedCount = 0;
			if (moonlitCount >= MOON_MOVE_STACKS) {
				context.accept(moveToBlack.getModified(event));
			}
		}
		else if (event.buffIdMatches(MOONSHADOWED)) {
			moonshadowedCount = nextMoonCount(moonshadowedCount);
			moonlitCount = 0;
			if (moonshadowedCount >= MOON_MOVE_STACKS) {
				context.accept(moveToWhite.getModified(event));
			}
		}
	}

	private static int nextMoonCount(Integer current) {
		int next = (current == null ? MOON_MOVE_STACKS - 1 : current) + 1;
		// 上限を超えたら死亡などでリセットされたとみなす
		return next > MOON_MOVE_STACKS ? 0 : next;
	}
}
