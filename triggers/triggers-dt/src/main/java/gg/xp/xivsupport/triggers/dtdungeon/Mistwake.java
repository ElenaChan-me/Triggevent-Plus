package gg.xp.xivsupport.triggers.dtdungeon;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.*;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/dungeon/mistwake.ts をもとに移植
@CalloutRepo(name = "Mistwake", duty = KnownDuty.Mistwake)
public class Mistwake extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public Mistwake(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.Mistwake);
	}

	// 1ボス目（Treno Catoblepas）

	@NpcCastCallout(0xA93F)
	private final ModifiableCallout<AbilityCastStart> earthquake = ModifiableCallout.durationBasedCall("Earthquake", "Raidwide");

	@NpcCastCallout(0xA943)
	private final ModifiableCallout<AbilityCastStart> thunderII = ModifiableCallout.durationBasedCall("Thunder II", "Spread, Away from Rocks");

	@NpcCastCallout(0xA942)
	private final ModifiableCallout<AbilityCastStart> bedevilingLight = ModifiableCallout.durationBasedCall("Bedeviling Light", "Hide Behind Rock");

	@NpcCastCallout(0xA941)
	private final ModifiableCallout<AbilityCastStart> thunderIIICatoblepas = ModifiableCallout.durationBasedCall("Thunder III (Treno Catoblepas)", "Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> rayOfLightningOnYou = ModifiableCallout.durationBasedCall("Ray of Lightning: On You", "Point Laser Away from Rocks");
	private final ModifiableCallout<AbilityCastStart> rayOfLightningOther = ModifiableCallout.durationBasedCall("Ray of Lightning: Not on You", "Stack on {event.target}");

	@HandleEvents
	public void rayOfLightning(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0xAF19)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(rayOfLightningOnYou.getModified(event));
		}
		else {
			context.accept(rayOfLightningOther.getModified(event));
		}
	}

	@NpcCastCallout(0xA947)
	private final ModifiableCallout<AbilityCastStart> petribreath = ModifiableCallout.durationBasedCall("Petribreath", "Away from Front");

	// 2ボス目（Amdusias）

	@NpcCastCallout(0xB118)
	private final ModifiableCallout<AbilityCastStart> thunderclapConcertoBehind = ModifiableCallout.durationBasedCall("Thunderclap Concerto (Behind Safe)", "Behind");

	@NpcCastCallout(0xB11D)
	private final ModifiableCallout<AbilityCastStart> thunderclapConcertoFront = ModifiableCallout.durationBasedCall("Thunderclap Concerto (Front Safe)", "Front");

	@NpcCastCallout(0xB126)
	private final ModifiableCallout<AbilityCastStart> thunderIV = ModifiableCallout.durationBasedCall("Thunder IV", "Raidwide");

	// 実ダメージの B121 には詠唱バーが無いので、対になる B120 で呼ぶ
	@NpcCastCallout(0xB120)
	private final ModifiableCallout<AbilityCastStart> bioII = ModifiableCallout.durationBasedCall("Bio II", "Raidwide");

	// 先に来る B12B は対象を持たないので、対象付きの B12C を使う
	@NpcCastCallout(0xB12C)
	private final ModifiableCallout<AbilityCastStart> shockbolt = ModifiableCallout.durationBasedCall("Shockbolt", "Buster on {event.target}");

	@NpcCastCallout(0xB129)
	private final ModifiableCallout<AbilityCastStart> thunderIIIAmdusias = ModifiableCallout.durationBasedCall("Thunder III (Amdusias)", "Stack on {event.target}");

	// 3ボス目（Thundergust Griffin）

	@NpcCastCallout(0xB0EB)
	private final ModifiableCallout<AbilityCastStart> thunderspark = ModifiableCallout.durationBasedCall("Thunderspark", "Raidwide");

	@NpcCastCallout(0xB0EC)
	private final ModifiableCallout<AbilityCastStart> highVolts = ModifiableCallout.durationBasedCall("High Volts", "Spread");

	@NpcCastCallout(0xB0F9)
	private final ModifiableCallout<AbilityCastStart> goldenTalons = ModifiableCallout.durationBasedCall("Golden Talons", "Buster on {event.target}");

}
