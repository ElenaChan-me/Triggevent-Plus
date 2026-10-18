package gg.xp.xivsupport.triggers.dtun;

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

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/trial/tsukuyomi-un.ts をもとに移植
@CalloutRepo(name = "Tsukuyomi Unreal", duty = KnownDuty.TsukuyomiUn)
public class TsukuyomiUn extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;
	private volatile boolean moonIsOut;
	// null は月光/月影のカウント未開始を表す
	private volatile Integer moonlitCount;
	private volatile Integer moonshadowedCount;

	public TsukuyomiUn(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TsukuyomiUn);
	}

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		moonIsOut = false;
		moonlitCount = null;
		moonshadowedCount = null;
	}

	@NpcCastCallout(0xB12E)
	private final ModifiableCallout<AbilityCastStart> reprimand = ModifiableCallout.durationBasedCall("Reprimand", "Raidwide");

	@NpcCastCallout(0xB130)
	private final ModifiableCallout<AbilityCastStart> nightfallGun = ModifiableCallout.durationBasedCall("Nightfall (Gun)", "Stack");

	@NpcCastCallout(0xB131)
	private final ModifiableCallout<AbilityCastStart> nightfallSpear = ModifiableCallout.durationBasedCall("Nightfall (Spear)", "Role Positions");

	private final ModifiableCallout<AbilityCastStart> tormentOnYou = ModifiableCallout.durationBasedCall("Torment Unto Death (on You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> tormentSwap = ModifiableCallout.durationBasedCall("Torment Unto Death (Other Tank)", "Tank Swap");
	private final ModifiableCallout<AbilityCastStart> tormentHealer = ModifiableCallout.durationBasedCall("Torment Unto Death (Healer)", "Buster on {event.target}");
	private final ModifiableCallout<AbilityCastStart> tormentOther = ModifiableCallout.durationBasedCall("Torment Unto Death (Other)", "Out of Front");

	@HandleEvents
	public void torment(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0xB12F, 0xB16A)) {
			return;
		}
		Job job = state.getPlayerJob();
		if (event.getTarget().isThePlayer()) {
			context.accept(tormentOnYou.getModified(event));
		}
		else if (job != null && job.isTank()) {
			context.accept(tormentSwap.getModified(event));
		}
		else if (job != null && job.isHealer()) {
			context.accept(tormentHealer.getModified(event));
		}
		else {
			context.accept(tormentOther.getModified(event));
		}
	}

	@HandleEvents
	public void moonTracking(EventContext context, BuffApplied event) {
		if (event.getTarget().isPc()) {
			return;
		}
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
		if (event.abilityIdMatches(0xB14E)) {
			context.accept((moonIsOut ? darkBladeOut : darkBladeIn).getModified(event));
		}
		else if (event.abilityIdMatches(0xB14F)) {
			context.accept((moonIsOut ? brightBladeOut : brightBladeIn).getModified(event));
		}
	}

	@PlayerHeadmarker(0x83)
	private final ModifiableCallout<HeadMarkerEvent> meteor = new ModifiableCallout<>("Meteor Marker", "Meteor on You");

	private final ModifiableCallout<HeadMarkerEvent> lunacy = new ModifiableCallout<>("Lunacy", "Stack on {event.target}");

	@HandleEvents
	public void lunacyMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(lunacy.getModified(event));
		}
	}

	@PlayerHeadmarker(0x17)
	private final ModifiableCallout<HeadMarkerEvent> hagetsu = new ModifiableCallout<>("Hagetsu", "Spread");

	// 残りHP35%で詠唱なしに始まるため、台詞で判定する
	private final ModifiableCallout<BattleTalkEvent> danceOfTheDead = new ModifiableCallout<>("Dance of the Dead", "Raidwide");

	@HandleEvents
	public void danceOfTheDeadTalk(EventContext context, BattleTalkEvent event) {
		if (event.getInstanceContentTextId() == 0x4C41) {
			context.accept(danceOfTheDead.getModified(event));
		}
	}

	@HandleEvents
	public void supremeSelenomancy(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0xB169)) {
			moonlitCount = null;
			moonshadowedCount = null;
		}
	}

	private final ModifiableCallout<BuffApplied> moonlit = new ModifiableCallout<BuffApplied>("Moonlit", "Move to Black").statusIcon(0x602);
	private final ModifiableCallout<BuffApplied> moonshadowed = new ModifiableCallout<BuffApplied>("Moonshadowed", "Move to White").statusIcon(0x603);

	@HandleEvents
	public void moonDebuffs(EventContext context, BuffApplied event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		// 初回に移動指示を出したいので 3 から数え始め、4 を超えたら周回し直す
		if (event.buffIdMatches(0x602)) {
			int count = (moonlitCount == null ? 3 : moonlitCount) + 1;
			if (count > 4) {
				count = 0;
			}
			moonlitCount = count;
			moonshadowedCount = 0;
			if (count >= 4) {
				context.accept(moonlit.getModified(event));
			}
		}
		else if (event.buffIdMatches(0x603)) {
			int count = (moonshadowedCount == null ? 3 : moonshadowedCount) + 1;
			if (count > 4) {
				count = 0;
			}
			moonshadowedCount = count;
			moonlitCount = 0;
			if (count >= 4) {
				context.accept(moonshadowed.getModified(event));
			}
		}
	}

}
