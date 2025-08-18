import {useMemo, useState} from "react";
import {MaterialReactTable, MRT_ColumnDef, useMaterialReactTable} from "material-react-table";
import {AssertedMapping, Mapping} from "../../model/Mapping.ts";

function AssertedMappings({ mapping }: { mapping: Mapping }) {
    const assertedMappingColumns = useMemo<MRT_ColumnDef<AssertedMapping>[]>(
        () => [
            {
                accessorKey: "mappingId",
                header: "Mapping Id"
            },
            {
                accessorKey: "subjectId",
                header: "Subject Id",
            },
            {
                accessorKey: "predicateId",
                header: "Predicate Id",
            },
            {
                accessorKey: "objectId",
                header: "Object Id",
            },
            {
                accessorKey: "mappingJustification",
                header: "Mapping Justification",
            },
        ],
        []
    );

    const [columnFilters, setColumnFilters] = useState<any[]>([]);
    const [columnVisibility] = useState({ mappingId: false })

    const assertedMappingsTable = useMaterialReactTable({
        columns: assertedMappingColumns,
        data: mapping.assertedMappings || [],
        onColumnFiltersChange: setColumnFilters,
        state: {
            columnFilters,
            columnVisibility
        },
        enableFilterMatchHighlighting: true,
        enableGlobalFilter: false,
        enableFullScreenToggle: false,
        enableDensityToggle: false,
        enableHiding: false,
        enableTopToolbar: false,
        enablePagination: false,
    });

    return (
        <div>
            <h1 className="section-subheading">Asserted Mappings</h1>
            <MaterialReactTable table={assertedMappingsTable}/>
        </div>
    );
}
export default AssertedMappings;