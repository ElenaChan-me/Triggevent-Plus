package gg.xp.xivsupport.triggers.cactbot.ew;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/dungeon/the_tower_of_zot.ts をもとに移植
@CalloutRepo(name = "The Tower Of Zot", duty = KnownDuty.TheTowerOfZot)
public class TheTowerOfZot extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public TheTowerOfZot(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheTowerOfZot);
	}

	// トランスミュートの何番目の玉が炎/バイオか。0 は未設置
	private int orbCount;
	private int fireOrb;
	private int bioOrb;

	private void resetOrbs() {
		orbCount = 0;
		fireOrb = 0;
		bioOrb = 0;
	}

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		resetOrbs();
	}

	// ミンドルーヴァ

	// 62A9 は三姉妹で共通、62CA は最終戦、62C0 はサンドゥルーヴァの最終戦
	@NpcCastCallout({0x62A9, 0x62CA, 0x62C0})
	private final ModifiableCallout<AbilityCastStart> bio = ModifiableCallout.durationBasedCall("Bio / Isitva Siddhi", "Buster on {event.target}");

	@HandleEvents
	public void transmute(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x629A, 0x631B, 0x631C, 0x631D)) {
			orbCount++;
			if (event.abilityIdMatches(0x629A)) {
				fireOrb = orbCount;
			}
			else if (event.abilityIdMatches(0x631D)) {
				bioOrb = orbCount;
			}
		}
		else if (event.abilityIdMatches(0x629C)) {
			// トランスミュートの一連の後に必ずドゥルパドが来る
			resetOrbs();
		}
	}

	private final ModifiableCallout<AbilityCastStart> manusyaFireOrb = ModifiableCallout.durationBasedCall("Manusya III (Fire Orb)", "Under Orb {num}");
	private final ModifiableCallout<AbilityCastStart> manusyaBioOrb = ModifiableCallout.durationBasedCall("Manusya III (Bio Orb)", "Behind Orb {num}");
	private final ModifiableCallout<AbilityCastStart> manusyaFireThenBio = ModifiableCallout.durationBasedCall("Manusya Fire III (Bio Orb)", "Get Under => Behind Orb {num}");
	private final ModifiableCallout<AbilityCastStart> manusyaBioThenFire = ModifiableCallout.durationBasedCall("Manusya Bio III (Fire Orb)", "Get Behind => Under Orb {num}");
	private final ModifiableCallout<AbilityCastStart> manusyaFire = ModifiableCallout.durationBasedCall("Manusya Fire III", "Get Under");
	private final ModifiableCallout<AbilityCastStart> manusyaBio = ModifiableCallout.durationBasedCall("Manusya Bio III", "Get Behind");

	@HandleEvents
	public void manusya(EventContext context, AbilityCastStart event) {
		// 6291 炎, 6292 氷, 6293 雷, 6294 バイオ
		if (event.abilityIdMatches(0x6292, 0x6293)) {
			if (fireOrb > 0) {
				context.accept(manusyaFireOrb.getModified(event, Map.of("num", fireOrb)));
			}
			else if (bioOrb > 0) {
				context.accept(manusyaBioOrb.getModified(event, Map.of("num", bioOrb)));
			}
		}
		else if (event.abilityIdMatches(0x6291)) {
			if (bioOrb > 0) {
				context.accept(manusyaFireThenBio.getModified(event, Map.of("num", bioOrb)));
			}
			else {
				context.accept(manusyaFire.getModified(event));
			}
		}
		else if (event.abilityIdMatches(0x6294)) {
			if (fireOrb > 0) {
				context.accept(manusyaBioThenFire.getModified(event, Map.of("num", fireOrb)));
			}
			else {
				context.accept(manusyaBio.getModified(event));
			}
		}
	}

	// サンドゥルーヴァ

	private final ModifiableCallout<AbilityUsedEvent> manusyaBerserk = new ModifiableCallout<>("Manusya Berserk", "Go Behind Empty Spot");

	@HandleEvents
	public void manusyaBerserk(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x62A1, 0x62BC) && event.isFirstTarget()) {
			context.accept(manusyaBerserk.getModified(event));
		}
	}

	@NpcCastCallout(0x62A5)
	private final ModifiableCallout<AbilityCastStart> manusyaConfuse = ModifiableCallout.durationBasedCall("Manusya Confuse", "Go Behind Still Clone");

	// チンドゥルーヴァ

	@NpcCastCallout(0x62B9)
	private final ModifiableCallout<AbilityCastStart> samsara = ModifiableCallout.durationBasedCall("Samsara", "Raidwide");

	@NpcCastCallout(0x62B8)
	private final ModifiableCallout<AbilityCastStart> deltaThunder = ModifiableCallout.durationBasedCall("Delta Thunder III", "Stack on {event.target}");

}
